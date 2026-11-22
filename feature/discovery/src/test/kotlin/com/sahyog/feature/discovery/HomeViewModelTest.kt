package com.sahyog.feature.discovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.core.testing.TestUserRepository
import com.sahyog.feature.discovery.home.HomeEvent
import com.sahyog.feature.discovery.home.HomeUiState
import com.sahyog.feature.discovery.home.HomeViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val users = TestUserRepository().apply {
        setUser(User("u1", "9876543210", "Asha Devi", setOf(UserRole.Customer), workerStatus = null))
    }
    private val catalog = TestCatalogRepository()

    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { HomeViewModel(users, catalog) }

    @Test
    fun `refreshes on start and shows cached catalog with first name`() = runTest {
        catalog.setCatalog(TestCatalog.categories, TestCatalog.workers)
        viewModel.uiState.test {
            val ready = awaitState<HomeUiState.Ready>()
            assertEquals("Asha", ready.firstName)
            assertEquals(TestCatalog.categories, ready.categories)
            assertEquals(TestCatalog.workers, ready.topWorkers)
            assertFalse(ready.isRefreshing)
        }
        assertEquals(1, catalog.refreshCount)
    }

    @Test
    fun `failure with nothing cached shows a full screen error and retry recovers`() = runTest {
        catalog.refreshResult = AppResult.Failure(AppError.Network)
        viewModel.uiState.test {
            assertEquals(AppError.Network, awaitState<HomeUiState.Error>().error)

            catalog.refreshResult = AppResult.Success(Unit)
            catalog.setCatalog(TestCatalog.categories, TestCatalog.workers)
            viewModel.onRetry()
            awaitState<HomeUiState.Ready>()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, catalog.refreshCount)
    }

    @Test
    fun `failure with cached data keeps content and reports it`() = runTest {
        catalog.setCatalog(TestCatalog.categories, TestCatalog.workers)
        catalog.refreshResult = AppResult.Failure(AppError.Network)
        viewModel.eventFlow.test {
            assertEquals(HomeEvent.RefreshFailed(AppError.Network), awaitItem())
        }
        viewModel.uiState.test { awaitState<HomeUiState.Ready>() }
    }

    @Test
    fun `successful refresh with no categories shows the empty state`() = runTest {
        viewModel.uiState.test { awaitState<HomeUiState.Empty>() }
    }

    @Test
    fun `pull to refresh downloads again`() = runTest {
        catalog.setCatalog(TestCatalog.categories, TestCatalog.workers)
        viewModel.uiState.test {
            awaitState<HomeUiState.Ready>()
            viewModel.onRefresh()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, catalog.refreshCount)
    }

    private suspend inline fun <reified T : HomeUiState> ReceiveTurbine<HomeUiState>.awaitState(): T {
        while (true) {
            val item = awaitItem()
            if (item is T) return item
        }
    }
}
