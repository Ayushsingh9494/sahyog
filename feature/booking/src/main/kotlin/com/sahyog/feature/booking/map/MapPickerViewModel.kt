package com.sahyog.feature.booking.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.location.AddressLookup
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.ui.location.LocationPermissionResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the map opens. */
data class MapStart(val point: GeoPoint, val zoom: Float)

/**
 * @property start Null until the opening position is known (it may come from storage).
 * @property center The point under the pin, once the map has settled.
 * @property address What that point looks like as an address; null if unknown.
 */
data class MapPickerUiState(
    val start: MapStart? = null,
    val center: GeoPoint? = null,
    val address: String? = null,
    val isLookingUp: Boolean = false,
    val isLocating: Boolean = false,
    val hasPermission: Boolean = false,
) {
    val canConfirm: Boolean get() = center != null
}

sealed interface MapPickerEvent {
    /** Animate the map to [point]. */
    data class MoveTo(val point: GeoPoint) : MapPickerEvent

    /** Ask for the location permission (the screen owns the system dialog). */
    data object RequestPermission : MapPickerEvent

    data class Problem(val kind: LocationProblem) : MapPickerEvent

    /** The customer confirmed; [point] is already at doorstep precision. */
    data class Picked(val point: GeoPoint, val address: String?) : MapPickerEvent
}

enum class LocationProblem { PermissionDenied, PermissionDeniedForGood, LocationOff, Unavailable }

/**
 * Pin-drop location picker: the pin stays in the middle and the customer moves the map
 * under it. Each time the map settles, the point is turned into an address.
 */
@HiltViewModel(assistedFactory = MapPickerViewModel.Factory::class)
class MapPickerViewModel @AssistedInject constructor(
    @Assisted initial: GeoPoint?,
    private val locationRepository: LocationRepository,
    private val addressLookup: AddressLookup,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        /** @param initial A pin chosen earlier, to adjust it rather than start over. */
        fun create(initial: GeoPoint?): MapPickerViewModel
    }

    private val state = MutableStateFlow(MapPickerUiState(hasPermission = locationRepository.hasPermission()))
    val uiState: StateFlow<MapPickerUiState> = state.asStateFlow()

    private val events = Channel<MapPickerEvent>(Channel.BUFFERED)
    val eventFlow: Flow<MapPickerEvent> = events.receiveAsFlow()

    private var lookup: Job? = null

    init {
        viewModelScope.launch {
            val start = when {
                initial != null -> MapStart(initial, PIN_ZOOM)
                else -> locationRepository.location.first()?.let { MapStart(it, AREA_ZOOM) }
            }
            state.update { it.copy(start = start ?: MapStart(INDIA, COUNTRY_ZOOM)) }
            // A fresh pick with permission already granted: go straight to where they are.
            if (initial == null && state.value.hasPermission) locate()
        }
    }

    fun onCameraIdle(point: GeoPoint) {
        state.update { it.copy(center = point, isLookingUp = true) }
        lookup?.cancel()
        lookup = viewModelScope.launch {
            val address = addressLookup.addressAt(point)
            state.update { it.copy(address = address, isLookingUp = false) }
        }
    }

    /** "My location": jumps to the device's position, asking for permission first if needed. */
    fun onMyLocation() {
        if (!locationRepository.hasPermission()) {
            viewModelScope.launch { events.send(MapPickerEvent.RequestPermission) }
            return
        }
        locate()
    }

    fun onPermissionResult(result: LocationPermissionResult) {
        when (result) {
            LocationPermissionResult.Granted -> {
                state.update { it.copy(hasPermission = true) }
                locate()
            }
            LocationPermissionResult.Denied -> problem(LocationProblem.PermissionDenied)
            LocationPermissionResult.PermanentlyDenied -> problem(LocationProblem.PermissionDeniedForGood)
        }
    }

    fun onConfirm() {
        val center = state.value.center ?: return
        // Don't confirm an address for a point the map has since moved away from.
        val address = state.value.address.takeUnless { state.value.isLookingUp }
        viewModelScope.launch { events.send(MapPickerEvent.Picked(center.doorstep(), address)) }
    }

    private fun locate() {
        if (state.value.isLocating) return
        state.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val result = locationRepository.updateFromDevice()
            state.update { it.copy(isLocating = false) }
            events.send(
                when (result) {
                    is DeviceLocationResult.Found -> MapPickerEvent.MoveTo(result.point)
                    DeviceLocationResult.PermissionDenied -> MapPickerEvent.Problem(LocationProblem.PermissionDenied)
                    DeviceLocationResult.LocationOff -> MapPickerEvent.Problem(LocationProblem.LocationOff)
                    DeviceLocationResult.Unavailable -> MapPickerEvent.Problem(LocationProblem.Unavailable)
                },
            )
        }
    }

    private fun problem(kind: LocationProblem) {
        viewModelScope.launch { events.send(MapPickerEvent.Problem(kind)) }
    }

    companion object {
        /** Street level, to place the pin on a door. */
        const val PIN_ZOOM = 18f

        /** Neighbourhood level, around a remembered (~1 km) location. */
        const val AREA_ZOOM = 15f
        const val COUNTRY_ZOOM = 4.5f

        /** Roughly the middle of India, when nothing better is known. */
        val INDIA = GeoPoint(22.5, 79.0)
    }
}
