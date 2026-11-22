package com.sahyog.feature.discovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.feature.discovery.worker.WorkerProfileUiState
import com.sahyog.feature.discovery.worker.WorkerProfileViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkerProfileViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val catalog = TestCatalogRepository().apply {
        setCatalog(TestCatalog.categories, TestCatalog.workers)
    }

    @Test
    fun `shows the worker`() = runTest {
        WorkerProfileViewModel("w1", catalog).uiState.test {
            assertEquals(WorkerProfileUiState.Ready(TestCatalog.ramesh), awaitLoaded())
        }
    }

    @Test
    fun `worker removed from the catalog becomes not found`() = runTest {
        WorkerProfileViewModel("w1", catalog).uiState.test {
            awaitLoaded()
            catalog.setCatalog(TestCatalog.categories, listOf(TestCatalog.rajesh))
            assertEquals(WorkerProfileUiState.NotFound, awaitItem())
        }
    }

    private suspend fun ReceiveTurbine<WorkerProfileUiState>.awaitLoaded(): WorkerProfileUiState {
        var item = awaitItem()
        while (item == WorkerProfileUiState.Loading) item = awaitItem()
        return item
    }
}
