package com.sahyog.feature.discovery.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The customer's filter choices on a category. Distance options apply only with a location. */
data class CategoryFilters(
    val availableOnly: Boolean = false,
    val nearestFirst: Boolean = false,
    /** Null means any distance. */
    val radiusKm: Int? = null,
)

sealed interface CategoryUiState {
    data object Loading : CategoryUiState

    /** The category was removed from the catalog (e.g. after a refresh). */
    data object NotFound : CategoryUiState

    /**
     * @property workers Already filtered and sorted.
     * @property totalCount Workers in the category before filtering.
     * @property distances Km by worker id; empty when the customer's location is unknown.
     */
    data class Ready(
        val category: ServiceCategory,
        val workers: List<Worker>,
        val filters: CategoryFilters,
        val totalCount: Int,
        val hasLocation: Boolean = false,
        val distances: Map<String, Double> = emptyMap(),
    ) : CategoryUiState
}

@HiltViewModel(assistedFactory = CategoryViewModel.Factory::class)
class CategoryViewModel @AssistedInject constructor(
    @Assisted categoryId: String,
    catalogRepository: CatalogRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(categoryId: String): CategoryViewModel
    }

    private val filters = MutableStateFlow(CategoryFilters())

    val uiState: StateFlow<CategoryUiState> = combine(
        catalogRepository.categories,
        catalogRepository.workers(categoryId),
        filters,
        locationRepository.location,
    ) { categories, workers, filters, here ->
        val category = categories.find { it.id == categoryId }
        when {
            categories.isEmpty() -> CategoryUiState.Loading
            category == null -> CategoryUiState.NotFound
            else -> {
                val distances = here?.let { point ->
                    workers.mapNotNull { w -> w.location?.let { w.id to point.distanceKmTo(it) } }.toMap()
                }.orEmpty()
                // Distance filters are ignored (not applied) when the location is unknown.
                val useDistance = here != null
                val shown = workers
                    .filter { !filters.availableOnly || it.isAvailable }
                    .filter { worker ->
                        val radius = filters.radiusKm
                        !useDistance || radius == null || (distances[worker.id] ?: Double.MAX_VALUE) <= radius
                    }
                    .let { list ->
                        if (useDistance && filters.nearestFirst) list.sortedBy { distances[it.id] ?: Double.MAX_VALUE } else list
                    }
                CategoryUiState.Ready(
                    category = category,
                    workers = shown,
                    filters = filters,
                    totalCount = workers.size,
                    hasLocation = useDistance,
                    distances = distances,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState.Loading)

    fun onAvailableOnlyChange(enabled: Boolean) = filters.update { it.copy(availableOnly = enabled) }

    fun onNearestFirstChange(enabled: Boolean) = filters.update { it.copy(nearestFirst = enabled) }

    fun onRadiusChange(km: Int?) = filters.update { it.copy(radiusKm = km) }

    /** "Show all workers" from the empty state. */
    fun onClearFilters() = filters.update { CategoryFilters(nearestFirst = it.nearestFirst) }
}
