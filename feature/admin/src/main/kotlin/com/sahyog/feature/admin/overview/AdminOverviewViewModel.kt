package com.sahyog.feature.admin.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AdminRepository
import com.sahyog.core.model.AdminOverview
import com.sahyog.core.model.VerificationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdminOverviewUiState {
    data object Loading : AdminOverviewUiState

    data class Error(val error: AppError) : AdminOverviewUiState

    /** @property nextInQueue The longest-waiting workers, previewed on the dashboard. */
    data class Ready(
        val overview: AdminOverview,
        val nextInQueue: List<VerificationRequest>,
        val isRefreshing: Boolean = false,
    ) : AdminOverviewUiState
}

/** The admin home: what needs attention now, and how the cooperative is doing. */
@HiltViewModel
class AdminOverviewViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val state = MutableStateFlow<AdminOverviewUiState>(AdminOverviewUiState.Loading)
    val uiState: StateFlow<AdminOverviewUiState> = state.asStateFlow()

    init {
        load()
    }

    fun onRefresh() = load()

    private fun load() {
        val current = state.value
        if (current is AdminOverviewUiState.Ready) {
            if (current.isRefreshing) return
            state.value = current.copy(isRefreshing = true)
        }
        viewModelScope.launch {
            val overview = async { adminRepository.overview() }
            val queue = async { adminRepository.verificationQueue() }
            val o = overview.await()
            val q = queue.await()
            state.update { previous ->
                when {
                    o is AppResult.Success -> AdminOverviewUiState.Ready(
                        overview = o.data,
                        nextInQueue = (q as? AppResult.Success)?.data.orEmpty().take(QUEUE_PREVIEW),
                    )
                    // Keep showing what we had; the screen reports the failed refresh.
                    previous is AdminOverviewUiState.Ready -> previous.copy(isRefreshing = false)
                    else -> AdminOverviewUiState.Error((o as AppResult.Failure).error)
                }
            }
        }
    }

    private companion object {
        const val QUEUE_PREVIEW = 3
    }
}
