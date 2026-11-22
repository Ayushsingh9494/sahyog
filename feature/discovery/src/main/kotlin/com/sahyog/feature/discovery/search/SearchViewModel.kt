package com.sahyog.feature.discovery.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SearchResultsState {
    /** Nothing typed yet: suggest categories instead. */
    data class Idle(val categories: List<ServiceCategory>) : SearchResultsState

    data class Results(val query: String, val workers: List<Worker>) : SearchResultsState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    /**
     * What's in the search box. Exposed separately from the results (and updated
     * synchronously) so typing never lags; kept in SavedStateHandle to survive process death.
     */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(QUERY_KEY, "")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val matches = query
        .map { it.trim() }
        .distinctUntilChanged()
        // Search as the user types, but not on every keystroke.
        .debounce { if (it.isEmpty()) 0 else DEBOUNCE_MS }
        .flatMapLatest { q ->
            if (q.isEmpty()) flowOf(null) else catalogRepository.searchWorkers(q).map { q to it }
        }
        .onStart { emit(null) }

    val results: StateFlow<SearchResultsState> = combine(
        catalogRepository.categories,
        matches,
    ) { categories, match ->
        if (match == null) {
            SearchResultsState.Idle(categories)
        } else {
            SearchResultsState.Results(query = match.first, workers = match.second)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResultsState.Idle(emptyList()))

    fun onQueryChange(value: String) {
        savedStateHandle[QUERY_KEY] = value.take(MAX_QUERY_LENGTH)
    }

    fun onClear() = onQueryChange("")

    private companion object {
        const val QUERY_KEY = "query"
        const val DEBOUNCE_MS = 300L
        const val MAX_QUERY_LENGTH = 60
    }
}
