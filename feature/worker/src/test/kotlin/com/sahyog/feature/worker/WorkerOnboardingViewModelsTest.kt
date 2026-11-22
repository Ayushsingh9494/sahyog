package com.sahyog.feature.worker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.worker.WorkerErrorFields
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestLocationRepository
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.core.testing.TestJobRepository
import com.sahyog.core.testing.TestWorkerProfileRepository
import com.sahyog.core.testing.TestWorkerProfiles
import com.sahyog.feature.worker.home.WorkerHomeUiState
import com.sahyog.feature.worker.home.WorkerHomeViewModel
import com.sahyog.core.ui.location.LocationPermissionResult
import com.sahyog.feature.worker.profile.ServiceDraft
import com.sahyog.feature.worker.profile.WorkLocationProblem
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WorkerOnboardingViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val workers = TestWorkerProfileRepository()
    private val jobs = TestJobRepository()
    private val location = TestLocationRepository()
    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val catalog = TestCatalogRepository().apply { setCatalog(TestCatalog.categories, emptyList()) }

    // --- Home ---

    @Test
    fun `home asks a new worker to set up a profile`() = runTest {
        WorkerHomeViewModel(workers, jobs, clock).uiState.test {
            assertEquals(WorkerHomeUiState.NeedsProfile, awaitNot(WorkerHomeUiState.Loading))
        }
    }

    @Test
    fun `home shows the profile and toggles availability`() = runTest {
        workers.serverProfile = TestWorkerProfiles.verified
        val viewModel = WorkerHomeViewModel(workers, jobs, clock)
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
        val viewModel = WorkerHomeViewModel(workers, jobs, clock)
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
        val viewModel = WorkerProfileFormViewModel(catalog, workers, location)
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
        val viewModel = WorkerProfileFormViewModel(catalog, workers, location)
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
        val viewModel = WorkerProfileFormViewModel(catalog, workers, location)
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
    fun `work location comes from the device, coarsened, and is saved with the profile`() = runTest {
        workers.serverProfile = TestWorkerProfiles.verified
        location.nextResult = DeviceLocationResult.Found(GeoPoint(31.308512, 75.587134))
        val viewModel = WorkerProfileFormViewModel(catalog, workers, location)
        viewModel.uiState.test {
            awaitForm { it.isEditing }
            viewModel.onLocationPermissionResult(LocationPermissionResult.Granted)
            assertEquals(GeoPoint(31.31, 75.59), awaitForm { it.location != null }.location)
            viewModel.onLocationPermissionResult(LocationPermissionResult.PermanentlyDenied)
            assertEquals(
                WorkLocationProblem.PermanentlyDenied,
                awaitForm { it.locationProblem != null }.locationProblem,
            )
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.eventFlow.test {
            viewModel.onSave()
            awaitItem()
        }
        assertEquals(GeoPoint(31.31, 75.59), workers.savedInputs.single().location)
    }

    @Test
    fun `services can be added up to the limit and removed down to one`() = runTest {
        val viewModel = WorkerProfileFormViewModel(catalog, workers, location)
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
