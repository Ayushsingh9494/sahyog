package com.sahyog.feature.admin.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AdminRepository
import com.sahyog.core.model.AdminWorker
import com.sahyog.core.model.WorkerVerificationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkersUiState(
    val query: String = "",
    /** Null = everyone. */
    val status: WorkerVerificationStatus? = null,
    val workers: List<AdminWorker> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

/** Every worker the cooperative has, searchable by name, phone or area and filterable by status. */
@OptIn(FlowPreview::class)
@HiltViewModel
class WorkersDirectoryViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val state = MutableStateFlow(WorkersUiState())
    val uiState: StateFlow<WorkersUiState> = state.asStateFlow()

    private val query = MutableStateFlow("")
    private val status = MutableStateFlow<WorkerVerificationStatus?>(null)
    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // Wait for a pause in typing before searching the server.
            // collectLatest: a newer search cancels an older one still in flight.
            combine(query.debounce(SEARCH_DEBOUNCE_MS), status, reload) { q, s, _ -> q to s }
                .collectLatest { (q, s) -> load(q, s) }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        state.update { it.copy(query = value) }
    }

    fun onStatusSelect(value: WorkerVerificationStatus?) {
        status.value = value
        state.update { it.copy(status = value) }
    }

    fun onRefresh() = reload.update { it + 1 }

    /** Each time the tab is shown again (statuses may have changed on the Verify tab). */
    fun onScreenShown() = onRefresh()

    private suspend fun load(q: String, s: WorkerVerificationStatus?) {
        state.update { it.copy(isLoading = true, error = null) }
        when (val result = adminRepository.workers(s, q)) {
            is AppResult.Success -> state.update { it.copy(workers = result.data, isLoading = false) }
            is AppResult.Failure -> state.update { it.copy(isLoading = false, error = result.error) }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
