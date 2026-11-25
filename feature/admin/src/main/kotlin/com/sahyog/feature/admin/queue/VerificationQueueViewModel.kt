package com.sahyog.feature.admin.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AdminRepository
import com.sahyog.core.model.VerificationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

sealed interface QueueUiState {
    data object Loading : QueueUiState

    data class Error(val error: AppError) : QueueUiState

    /** Oldest first. [now] lets the screen show how long each person has waited. */
    data class Ready(
        val requests: List<VerificationRequest>,
        val now: Instant,
        val isRefreshing: Boolean = false,
    ) : QueueUiState
}

/** Workers waiting for the cooperative to check their ID. */
@HiltViewModel
class VerificationQueueViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow<QueueUiState>(QueueUiState.Loading)
    val uiState: StateFlow<QueueUiState> = state.asStateFlow()

    init {
        load()
    }

    /** Also called when returning from a decision, so the decided person disappears. */
    fun onRefresh() = load()

    private fun load() {
        val current = state.value
        if (current is QueueUiState.Ready) {
            if (current.isRefreshing) return
            state.value = current.copy(isRefreshing = true)
        }
        viewModelScope.launch {
            state.value = when (val result = adminRepository.verificationQueue()) {
                is AppResult.Success -> QueueUiState.Ready(result.data, clock.instant())
                is AppResult.Failure -> (state.value as? QueueUiState.Ready)?.copy(isRefreshing = false)
                    ?: QueueUiState.Error(result.error)
            }
        }
    }
}
