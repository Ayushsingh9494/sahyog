package com.sahyog.feature.discovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.core.testing.TestLocationRepository
import com.sahyog.core.testing.TestReviewRepository
import com.sahyog.core.testing.TestReviews
import com.sahyog.feature.discovery.worker.WorkerProfileUiState
import com.sahyog.feature.discovery.worker.WorkerProfileViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WorkerProfileViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC)
    private val catalog = TestCatalogRepository().apply {
        setCatalog(TestCatalog.categories, TestCatalog.workers)
    }
    private val reviews = TestReviewRepository()

    private fun viewModel() = WorkerProfileViewModel("w1", catalog, TestLocationRepository(), reviews, clock)

    @Test
    fun `shows the worker`() = runTest {
        viewModel().uiState.test {
            assertEquals(
                WorkerProfileUiState.Ready(TestCatalog.ramesh, today = LocalDate.of(2026, 11, 24)),
                awaitLoaded(),
            )
        }
    }

    @Test
    fun `shows the worker's reviews newest first and refreshes them on open`() = runTest {
        reviews.setReviews(
            listOf(
                TestReviews.review("old", "w1", createdAtMillis = 1),
                TestReviews.review("new", "w1", createdAtMillis = 2),
                TestReviews.review("other", "w2"),
            ),
        )
        viewModel().uiState.test {
            val ready = awaitLoaded() as WorkerProfileUiState.Ready
            assertEquals(listOf("new", "old"), ready.reviews.map { it.id })
        }
        assertEquals(listOf("w1"), reviews.refreshedWorkers)
    }

    @Test
    fun `worker removed from the catalog becomes not found`() = runTest {
        viewModel().uiState.test {
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
