package com.sahyog.feature.discovery.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.CatalogRepository
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

sealed interface CategoryUiState {
    data object Loading : CategoryUiState

    /** The category was removed from the catalog (e.g. after a refresh). */
    data object NotFound : CategoryUiState

    /**
     * @property workers Already filtered by [availableOnly].
     * @property totalCount Workers in the category before filtering.
     */
    data class Ready(
        val category: ServiceCategory,
        val workers: List<Worker>,
        val availableOnly: Boolean,
        val totalCount: Int,
    ) : CategoryUiState
}

@HiltViewModel(assistedFactory = CategoryViewModel.Factory::class)
class CategoryViewModel @AssistedInject constructor(
    @Assisted categoryId: String,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(categoryId: String): CategoryViewModel
    }

    private val availableOnly = MutableStateFlow(false)

    val uiState: StateFlow<CategoryUiState> = combine(
        catalogRepository.categories,
        catalogRepository.workers(categoryId),
        availableOnly,
    ) { categories, workers, onlyAvailable ->
        val category = categories.find { it.id == categoryId }
        when {
            categories.isEmpty() -> CategoryUiState.Loading
            category == null -> CategoryUiState.NotFound
            else -> CategoryUiState.Ready(
                category = category,
                workers = if (onlyAvailable) workers.filter { it.isAvailable } else workers,
                availableOnly = onlyAvailable,
                totalCount = workers.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState.Loading)

    fun onAvailableOnlyChange(enabled: Boolean) = availableOnly.update { enabled }
}
