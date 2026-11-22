package com.sahyog.feature.worker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.WorkerProfileRepository
import com.sahyog.core.data.repository.WorkerProfileState
import com.sahyog.core.model.WorkerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WorkerHomeUiState {
    data object Loading : WorkerHomeUiState

    /** Couldn't load the profile and nothing is known yet. */
    data class Error(val error: AppError) : WorkerHomeUiState

    /** Has the Worker role but no worker profile yet. */
    data object NeedsProfile : WorkerHomeUiState

    data class Ready(
        val profile: WorkerProfile,
        val isUpdatingAvailability: Boolean,
    ) : WorkerHomeUiState
}

sealed interface WorkerHomeEvent {
    data class Failed(val error: AppError) : WorkerHomeEvent
}

/** The worker's main tab: verification progress, the "Accepting jobs" switch and (M7b) jobs. */
@HiltViewModel
class WorkerHomeViewModel @Inject constructor(
    private val workerProfileRepository: WorkerProfileRepository,
) : ViewModel() {

    private val loadError = MutableStateFlow<AppError?>(null)
    private val updatingAvailability = MutableStateFlow(false)

    private val events = Channel<WorkerHomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<WorkerHomeEvent> = events.receiveAsFlow()

    val uiState: StateFlow<WorkerHomeUiState> = combine(
        workerProfileRepository.profile,
        loadError,
        updatingAvailability,
    ) { profile, error, updating ->
        when (profile) {
            WorkerProfileState.NotLoaded -> if (error != null) WorkerHomeUiState.Error(error) else WorkerHomeUiState.Loading
            WorkerProfileState.NotSetUp -> WorkerHomeUiState.NeedsProfile
            is WorkerProfileState.Ready -> WorkerHomeUiState.Ready(profile.profile, updating)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerHomeUiState.Loading)

    init {
        refresh()
    }

    fun onRetry() = refresh()

    /** Called periodically while verification is pending, to notice the decision. */
    fun onAutoRefresh() = refresh()

    fun onAvailabilityChange(available: Boolean) {
        if (updatingAvailability.value) return
        updatingAvailability.value = true
        viewModelScope.launch {
            val result = workerProfileRepository.setAvailability(available)
            updatingAvailability.value = false
            if (result is AppResult.Failure) events.send(WorkerHomeEvent.Failed(result.error))
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            loadError.value = null
            val result = workerProfileRepository.refresh()
            if (result is AppResult.Failure) loadError.value = result.error
        }
    }
}
