package com.sahyog.feature.worker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.worker.WorkerErrorFields
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.core.testing.TestWorkerProfileRepository
import com.sahyog.core.testing.TestWorkerProfiles
import com.sahyog.feature.worker.home.WorkerHomeUiState
import com.sahyog.feature.worker.home.WorkerHomeViewModel
import com.sahyog.feature.worker.profile.ServiceDraft
import com.sahyog.feature.worker.profile.WorkerProfileFormEvent
import com.sahyog.feature.worker.profile.WorkerProfileFormUiState
import com.sahyog.feature.worker.profile.WorkerProfileFormViewModel
import com.sahyog.feature.worker.verification.IdVerificationEvent
import com.sahyog.feature.worker.verification.IdVerificationViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkerOnboardingViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val workers = TestWorkerProfileRepository()
    private val catalog = TestCatalogRepository().apply { setCatalog(TestCatalog.categories, emptyList()) }

    // --- Home ---

    @Test
    fun `home asks a new worker to set up a profile`() = runTest {
        WorkerHomeViewModel(workers).uiState.test {
            assertEquals(WorkerHomeUiState.NeedsProfile, awaitNot(WorkerHomeUiState.Loading))
        }
    }

    @Test
    fun `home shows the profile and toggles availability`() = runTest {
        workers.serverProfile = TestWorkerProfiles.verified
        val viewModel = WorkerHomeViewModel(workers)
        viewModel.uiState.test {
            val ready = awaitNot(WorkerHomeUiState.Loading) as WorkerHomeUiState.Ready
            assertTrue(ready.profile.isAvailable)
            viewModel.onAvailabilityChange(false)
            var item = awaitItem()
            while (item !is WorkerHomeUiState.Ready || item.profile.isAvailable) item = awaitItem()
        }
        assertEquals(listOf(false), workers.availabilityChanges)
    }

    @Test
    fun `home load failure shows an error, retry recovers`() = runTest {
        workers.refreshResult = AppResult.Failure(AppError.Network)
        val viewModel = WorkerHomeViewModel(workers)
        viewModel.uiState.test {
            assertEquals(WorkerHomeUiState.Error(AppError.Network), awaitNot(WorkerHomeUiState.Loading))
            workers.refreshResult = AppResult.Success(Unit)
            viewModel.onRetry()
            var item = awaitItem()
            while (item != WorkerHomeUiState.NeedsProfile) item = awaitItem()
        }
    }

    // --- Profile form ---

    @Test
    fun `invalid form shows errors instead of saving`() = runTest {
        val viewModel = WorkerProfileFormViewModel(catalog, workers)
        viewModel.uiState.test {
            awaitForm()
            viewModel.onSave()
            val form = awaitForm { it.showErrors }
            assertFalse(form.categoriesValid)
            assertFalse(form.servicesValid)
        }
        assertTrue(workers.savedInputs.isEmpty())
    }

    @Test
    fun `valid new profile is saved and continues to ID verification`() = runTest {
        val viewModel = WorkerProfileFormViewModel(catalog, workers)
        viewModel.uiState.test {
            awaitForm()
            viewModel.onCategoryToggle("electrician")
            viewModel.onServiceChange(0, ServiceDraft(" Fan installation ", "2a50", PriceUnit.PerVisit))
            viewModel.onLocalityChange("Model Town")
            viewModel.onLanguageToggle("Punjabi")
            viewModel.onLanguageToggle("Hindi")
            viewModel.onYearsChange("7 yrs")
            val form = awaitForm { it.isValid }
            // Non-digits are dropped as they're typed.
            assertEquals("250", form.services.single().priceRupees)
            assertEquals("7", form.yearsExperience)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.eventFlow.test {
            viewModel.onSave()
            assertEquals(WorkerProfileFormEvent.Saved(needsIdDocument = true), awaitItem())
        }
        val input = workers.savedInputs.single()
        assertEquals("Fan installation", input.services.single().name)
        assertEquals(Paise.rupees(250), input.services.single().startingPrice)
        // Languages are stored in the form's order, not tap order.
        assertEquals(listOf("Hindi", "Punjabi"), input.languages)
    }

    @Test
    fun `editing pre-fills the existing profile and needs no new ID`() = runTest {
        workers.serverProfile = TestWorkerProfiles.verified
        val viewModel = WorkerProfileFormViewModel(catalog, workers)
        viewModel.uiState.test {
            val form = awaitForm { it.isEditing }
            assertEquals(setOf("electrician"), form.selectedCategoryIds)
            assertEquals("250", form.services.single().priceRupees)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.eventFlow.test {
            viewModel.onSave()
            assertEquals(WorkerProfileFormEvent.Saved(needsIdDocument = false), awaitItem())
        }
    }

    @Test
    fun `services can be added up to the limit and removed down to one`() = runTest {
        val viewModel = WorkerProfileFormViewModel(catalog, workers)
        viewModel.uiState.test {
            awaitForm()
            viewModel.onRemoveService(0)
            repeat(10) { viewModel.onAddService() }
            val form = awaitForm { it.services.size == 8 }
            assertFalse(form.canAddService)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- ID verification ---

    @Test
    fun `ID needs a type and a photo before it can be submitted`() = runTest {
        val viewModel = IdVerificationViewModel(SavedStateHandle(), workers)
        viewModel.uiState.test {
            assertFalse(awaitItem().canSubmit)
            viewModel.onTypeSelect(IdDocumentType.VoterId)
            viewModel.onPhotoPicked(null) // backed out of the picker
            viewModel.onPhotoPicked("content://media/picker/0/1")
            var item = awaitItem()
            while (!item.canSubmit) item = awaitItem()
        }
        viewModel.eventFlow.test {
            viewModel.onSubmit()
            assertEquals(IdVerificationEvent.Submitted, awaitItem())
        }
        assertEquals(listOf(IdDocumentType.VoterId to "content://media/picker/0/1"), workers.submittedDocuments)
    }

    @Test
    fun `rejected photo is reported`() = runTest {
        workers.submitResult = AppResult.Failure(AppError.Validation(WorkerErrorFields.DOCUMENT_TOO_LARGE))
        val viewModel = IdVerificationViewModel(
            SavedStateHandle(mapOf("document_type" to "Aadhaar", "photo_uri" to "content://x")),
            workers,
        )
        viewModel.uiState.test {
            var item = awaitItem()
            while (!item.canSubmit) item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.eventFlow.test {
            viewModel.onSubmit()
            assertEquals(
                IdVerificationEvent.Failed(AppError.Validation(WorkerErrorFields.DOCUMENT_TOO_LARGE)),
                awaitItem(),
            )
        }
    }

    private suspend fun ReceiveTurbine<WorkerHomeUiState>.awaitNot(state: WorkerHomeUiState): WorkerHomeUiState {
        var item = awaitItem()
        while (item == state) item = awaitItem()
        return item
    }

    private suspend fun ReceiveTurbine<WorkerProfileFormUiState>.awaitForm(
        predicate: (WorkerProfileFormUiState) -> Boolean = { true },
    ): WorkerProfileFormUiState {
        while (true) {
            val item = awaitItem()
            if (!item.isLoading && predicate(item)) return item
        }
    }
}
