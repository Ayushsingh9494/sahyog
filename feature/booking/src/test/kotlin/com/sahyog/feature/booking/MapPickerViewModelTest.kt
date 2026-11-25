package com.sahyog.feature.booking

import app.cash.turbine.test
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestAddressLookup
import com.sahyog.core.testing.TestLocationRepository
import com.sahyog.core.testing.TestPlaces
import com.sahyog.core.ui.location.LocationPermissionResult
import com.sahyog.feature.booking.map.LocationProblem
import com.sahyog.feature.booking.map.MapPickerEvent
import com.sahyog.feature.booking.map.MapPickerViewModel
import com.sahyog.feature.booking.map.MapStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapPickerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val location = TestLocationRepository()
    private val lookup = TestAddressLookup(mapOf(TestPlaces.modelTown to "Model Town, Jalandhar"))

    private fun viewModel(initial: GeoPoint? = null) = MapPickerViewModel(initial, location, lookup)

    @Test
    fun `opens on an earlier pin, else the remembered area, else India`() = runTest {
        val pin = GeoPoint(31.30851, 75.58712)
        assertEquals(
            MapStart(pin, MapPickerViewModel.PIN_ZOOM),
            viewModel(pin).uiState.first { it.start != null }.start,
        )

        location.setLocation(TestPlaces.civilLines)
        assertEquals(
            MapStart(TestPlaces.civilLines, MapPickerViewModel.AREA_ZOOM),
            viewModel().uiState.first { it.start != null }.start,
        )

        location.setLocation(null)
        assertEquals(MapPickerViewModel.INDIA, viewModel().uiState.first { it.start != null }.start?.point)
    }

    @Test
    fun `with permission it jumps to the device location straight away`() = runTest {
        location.permissionGranted = true
        location.nextResult = DeviceLocationResult.Found(TestPlaces.modelTown)
        viewModel().eventFlow.test {
            assertEquals(MapPickerEvent.MoveTo(TestPlaces.modelTown), awaitItem())
        }
    }

    @Test
    fun `settling the map looks up the address, and confirming returns the doorstep point`() = runTest {
        val viewModel = viewModel()
        val precise = GeoPoint(31.308512345, 75.587198765)
        lookup.addresses = mapOf(precise to "Model Town, Jalandhar")
        viewModel.onCameraIdle(precise)
        val ready = viewModel.uiState.first { !it.isLookingUp && it.center != null }
        assertEquals("Model Town, Jalandhar", ready.address)

        viewModel.eventFlow.test {
            viewModel.onConfirm()
            assertEquals(MapPickerEvent.Picked(precise.doorstep(), "Model Town, Jalandhar"), awaitItem())
        }
    }

    @Test
    fun `an unknown place can still be confirmed, without an address`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(GeoPoint(10.0, 10.0))
        assertNull(viewModel.uiState.first { !it.isLookingUp && it.center != null }.address)
        viewModel.eventFlow.test {
            viewModel.onConfirm()
            assertEquals(MapPickerEvent.Picked(GeoPoint(10.0, 10.0), null), awaitItem())
        }
    }

    @Test
    fun `my location asks for permission first, and explains refusals and failures`() = runTest {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.hasPermission)
        viewModel.eventFlow.test {
            viewModel.onMyLocation()
            assertEquals(MapPickerEvent.RequestPermission, awaitItem())

            viewModel.onPermissionResult(LocationPermissionResult.PermanentlyDenied)
            assertEquals(MapPickerEvent.Problem(LocationProblem.PermissionDeniedForGood), awaitItem())

            location.permissionGranted = true
            location.nextResult = DeviceLocationResult.LocationOff
            viewModel.onPermissionResult(LocationPermissionResult.Granted)
            assertEquals(MapPickerEvent.Problem(LocationProblem.LocationOff), awaitItem())
        }
        assertTrue(viewModel.uiState.value.hasPermission)
    }
}
