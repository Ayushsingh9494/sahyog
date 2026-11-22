package com.sahyog.feature.discovery.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.ui.location.LocationPermissionResult
import com.sahyog.core.model.Worker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the "workers near you" flow is. */
enum class LocationStatus {
    /** Nothing in progress (location known, or never asked). */
    Idle,
    Locating,
    Denied,
    PermanentlyDenied,
    /** Location services are switched off on the phone. */
    Off,
    /** No fix arrived (e.g. indoors without network). */
    Unavailable,
}

sealed interface HomeUiState {
    /** Nothing cached yet and the first download is in progress. */
    data object Loading : HomeUiState

    /** Nothing cached and the download failed: a full-screen error with retry. */
    data class Error(val error: AppError) : HomeUiState

    /** Downloaded successfully, but the cooperative hasn't listed any services. */
    data object Empty : HomeUiState

    /**
     * @property isRefreshing True only for a refresh the user pulled for; background
     * refreshes update the content silently.
     */
    data class Ready(
        val firstName: String?,
        val categories: List<ServiceCategory>,
        val topWorkers: List<Worker>,
        val isRefreshing: Boolean,
        val hasLocation: Boolean = false,
        val locationStatus: LocationStatus = LocationStatus.Idle,
        /** Closest workers within [NEARBY_RADIUS_KM], nearest first. Empty without a location. */
        val nearby: List<Worker> = emptyList(),
        /** Distance in km by worker id, for every worker with a known location. */
        val distances: Map<String, Double> = emptyMap(),
    ) : HomeUiState
}

sealed interface HomeEvent {
    /** A refresh failed while cached content is on screen: say so without hiding it. */
    data class RefreshFailed(val error: AppError) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    userRepository: UserRepository,
    private val catalogRepository: CatalogRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val locationStatus = MutableStateFlow(LocationStatus.Idle)
    private val locationState = combine(locationRepository.location, locationStatus) { point, status -> point to status }

    private data class RefreshStatus(
        val inFlight: Boolean = false,
        val userInitiated: Boolean = false,
        val error: AppError? = null,
        val hasCompletedOnce: Boolean = false,
    )

    private val refreshStatus = MutableStateFlow(RefreshStatus())

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        userRepository.currentUser.map { it?.name?.trim()?.substringBefore(' ')?.ifBlank { null } },
        catalogRepository.categories,
        catalogRepository.workers(),
        refreshStatus,
        locationState,
    ) { firstName, categories, workers, status, (here, locating) ->
        val distances = here?.let { point ->
            workers.mapNotNull { worker -> worker.location?.let { worker.id to point.distanceKmTo(it) } }.toMap()
        }.orEmpty()
        when {
            categories.isNotEmpty() -> HomeUiState.Ready(
                hasLocation = here != null,
                locationStatus = locating,
                distances = distances,
                nearby = workers
                    .filter { (distances[it.id] ?: Double.MAX_VALUE) <= NEARBY_RADIUS_KM }
                    .sortedBy { distances[it.id] }
                    .take(TOP_WORKER_COUNT),
                firstName = firstName,
                categories = categories,
                topWorkers = workers.take(TOP_WORKER_COUNT),
                isRefreshing = status.inFlight && status.userInitiated,
            )
            status.inFlight -> HomeUiState.Loading
            status.error != null -> HomeUiState.Error(status.error)
            status.hasCompletedOnce -> HomeUiState.Empty
            else -> HomeUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

    init {
        // Keep the cache fresh every time Home is opened; cached content shows meanwhile.
        refresh(userInitiated = false)
        // Permission granted earlier: refresh the location quietly (no prompts on open).
        if (locationRepository.hasPermission()) viewModelScope.launch { locationRepository.updateFromDevice() }
    }

    /** The user answered the system permission dialog (after tapping "Use my location"). */
    fun onLocationPermissionResult(result: LocationPermissionResult) {
        when (result) {
            LocationPermissionResult.Granted -> locate()
            LocationPermissionResult.Denied -> locationStatus.value = LocationStatus.Denied
            LocationPermissionResult.PermanentlyDenied -> locationStatus.value = LocationStatus.PermanentlyDenied
        }
    }

    /** "Update" after the location is known, or "Try again" after it failed. */
    fun onRetryLocation() = locate()

    private fun locate() {
        if (locationStatus.value == LocationStatus.Locating) return
        locationStatus.value = LocationStatus.Locating
        viewModelScope.launch {
            locationStatus.value = when (locationRepository.updateFromDevice()) {
                is DeviceLocationResult.Found -> LocationStatus.Idle
                DeviceLocationResult.PermissionDenied -> LocationStatus.Denied
                DeviceLocationResult.LocationOff -> LocationStatus.Off
                DeviceLocationResult.Unavailable -> LocationStatus.Unavailable
            }
        }
    }

    fun onRefresh() = refresh(userInitiated = true)

    fun onRetry() = refresh(userInitiated = false)

    private fun refresh(userInitiated: Boolean) {
        if (refreshStatus.value.inFlight) return
        refreshStatus.value = RefreshStatus(inFlight = true, userInitiated = userInitiated)
        viewModelScope.launch {
            val result = catalogRepository.refresh()
            val error = (result as? AppResult.Failure)?.error
            if (error != null && catalogRepository.categories.first().isNotEmpty()) {
                events.send(HomeEvent.RefreshFailed(error))
                refreshStatus.value = RefreshStatus(hasCompletedOnce = true)
            } else {
                refreshStatus.value = RefreshStatus(error = error, hasCompletedOnce = true)
            }
        }
    }

    private companion object {
        const val TOP_WORKER_COUNT = 5
        const val NEARBY_RADIUS_KM = 25.0
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
