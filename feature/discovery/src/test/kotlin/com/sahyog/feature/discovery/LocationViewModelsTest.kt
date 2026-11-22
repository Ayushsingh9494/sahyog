package com.sahyog.feature.discovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.core.testing.TestLocationRepository
import com.sahyog.core.testing.TestPlaces
import com.sahyog.core.testing.TestUserRepository
import com.sahyog.core.ui.location.LocationPermissionResult
import com.sahyog.feature.discovery.category.CategoryUiState
import com.sahyog.feature.discovery.category.CategoryViewModel
import com.sahyog.feature.discovery.home.HomeUiState
import com.sahyog.feature.discovery.home.HomeViewModel
import com.sahyog.feature.discovery.home.LocationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocationViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // Ramesh in Civil Lines (~2.5 km from Model Town), Sunil in Model Town, Rajesh far away.
    private val ramesh = TestCatalog.ramesh.copy(location = TestPlaces.civilLines)
    private val sunil = TestCatalog.sunil.copy(location = TestPlaces.modelTown)
    private val rajesh = TestCatalog.rajesh.copy(location = TestPlaces.amritsar)

    private val users = TestUserRepository().apply {
        setUser(User("u1", "9876543210", "Asha", setOf(UserRole.Customer), workerStatus = null))
    }
    private val catalog = TestCatalogRepository().apply {
        setCatalog(TestCatalog.categories, listOf(ramesh, rajesh, sunil))
    }
    private val location = TestLocationRepository()

    @Test
    fun `no prompt and no device lookup without permission`() = runTest {
        HomeViewModel(users, catalog, location).uiState.test {
            val ready = awaitReady()
            assertFalse(ready.hasLocation)
            assertTrue(ready.nearby.isEmpty())
        }
        assertEquals(0, location.updateCount)
    }

    @Test
    fun `permission granted earlier refreshes the location quietly on open`() = runTest {
        location.permissionGranted = true
        location.nextResult = DeviceLocationResult.Found(TestPlaces.modelTown)
        HomeViewModel(users, catalog, location).uiState.test {
            val ready = awaitReady { it.hasLocation }
            // Nearest first, and the far-away worker (75 km) isn't "near you".
            assertEquals(listOf(sunil.id, ramesh.id), ready.nearby.map { it.id })
            assertEquals(LocationStatus.Idle, ready.locationStatus)
            assertTrue(ready.distances.getValue(rajesh.id) > 70)
        }
        assertEquals(1, location.updateCount)
    }

    @Test
    fun `granting permission locates, refusing explains, refusing for good sends to settings`() = runTest {
        val viewModel = HomeViewModel(users, catalog, location)
        viewModel.uiState.test {
            awaitReady()
            viewModel.onLocationPermissionResult(LocationPermissionResult.Denied)
            assertEquals(LocationStatus.Denied, awaitReady { it.locationStatus == LocationStatus.Denied }.locationStatus)

            viewModel.onLocationPermissionResult(LocationPermissionResult.PermanentlyDenied)
            awaitReady { it.locationStatus == LocationStatus.PermanentlyDenied }

            location.nextResult = DeviceLocationResult.LocationOff
            viewModel.onLocationPermissionResult(LocationPermissionResult.Granted)
            awaitReady { it.locationStatus == LocationStatus.Off }

            location.nextResult = DeviceLocationResult.Found(TestPlaces.modelTown)
            viewModel.onRetryLocation()
            awaitReady { it.hasLocation && it.locationStatus == LocationStatus.Idle }
        }
    }

    @Test
    fun `category sorts by distance and filters by radius once location is known`() = runTest {
        location.setLocation(TestPlaces.modelTown)
        val viewModel = CategoryViewModel("electrician", catalog, location)
        viewModel.uiState.test {
            assertEquals(listOf(ramesh.id, sunil.id), awaitCategory().workers.map { it.id }) // by rating
            viewModel.onNearestFirstChange(true)
            assertEquals(listOf(sunil.id, ramesh.id), awaitCategory { it.filters.nearestFirst }.workers.map { it.id })
            viewModel.onRadiusChange(2)
            assertEquals(listOf(sunil.id), awaitCategory { it.filters.radiusKm == 2 }.workers.map { it.id })
            viewModel.onClearFilters()
            assertEquals(2, awaitCategory { it.filters.radiusKm == null }.workers.size)
        }
    }

    @Test
    fun `distance filters are ignored without a location`() = runTest {
        val viewModel = CategoryViewModel("electrician", catalog, location)
        viewModel.uiState.test {
            awaitCategory()
            viewModel.onRadiusChange(2)
            val ready = awaitCategory { it.filters.radiusKm == 2 }
            assertFalse(ready.hasLocation)
            assertEquals(2, ready.workers.size)
        }
    }

    private suspend fun ReceiveTurbine<HomeUiState>.awaitReady(
        predicate: (HomeUiState.Ready) -> Boolean = { true },
    ): HomeUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is HomeUiState.Ready && predicate(item)) return item
        }
    }

    private suspend fun ReceiveTurbine<CategoryUiState>.awaitCategory(
        predicate: (CategoryUiState.Ready) -> Boolean = { true },
    ): CategoryUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is CategoryUiState.Ready && predicate(item)) return item
        }
    }
}
