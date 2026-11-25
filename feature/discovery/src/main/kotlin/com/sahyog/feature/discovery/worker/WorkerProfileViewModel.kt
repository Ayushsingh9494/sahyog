package com.sahyog.feature.discovery.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.data.repository.ReviewRepository
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerReview
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

sealed interface WorkerProfileUiState {
    data object Loading : WorkerProfileUiState

    /** The worker left the catalog (e.g. no longer active in the cooperative). */
    data object NotFound : WorkerProfileUiState

    /**
     * @property distanceKm From the customer, when both locations are known.
     * @property reviews Most recent first; empty if none were written (or not loaded yet).
     */
    data class Ready(
        val worker: Worker,
        val distanceKm: Double? = null,
        val reviews: List<WorkerReview> = emptyList(),
        val today: LocalDate = LocalDate.MIN,
    ) : WorkerProfileUiState
}

@HiltViewModel(assistedFactory = WorkerProfileViewModel.Factory::class)
class WorkerProfileViewModel @AssistedInject constructor(
    @Assisted workerId: String,
    private val catalogRepository: CatalogRepository,
    locationRepository: LocationRepository,
    private val reviewRepository: ReviewRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(workerId: String): WorkerProfileViewModel
    }

    val uiState: StateFlow<WorkerProfileUiState> = combine(
        catalogRepository.worker(workerId),
        locationRepository.location,
        reviewRepository.reviews(workerId),
    ) { worker, here, reviews ->
        if (worker == null) {
            WorkerProfileUiState.NotFound
        } else {
            val distance = here?.let { point -> worker.location?.let { point.distanceKmTo(it) } }
            WorkerProfileUiState.Ready(worker, distance, reviews, LocalDate.now(clock))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerProfileUiState.Loading)

    init {
        // Quietly: the cached profile and reviews stay on screen if either fails. The
        // catalog is refreshed too, so the rating matches the newest reviews.
        viewModelScope.launch { reviewRepository.refresh(workerId) }
        viewModelScope.launch { catalogRepository.refresh() }
    }
}
