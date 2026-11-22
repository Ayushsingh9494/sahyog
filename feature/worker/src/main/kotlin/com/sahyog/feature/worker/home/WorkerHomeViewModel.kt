package com.sahyog.feature.worker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.JobRepository
import com.sahyog.core.data.repository.WorkerProfileRepository
import com.sahyog.core.data.repository.WorkerProfileState
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.Job
import com.sahyog.core.model.WorkerProfile
import com.sahyog.core.model.WorkerVerificationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface WorkerHomeUiState {
    data object Loading : WorkerHomeUiState

    /** Couldn't load the profile and nothing is known yet. */
    data class Error(val error: AppError) : WorkerHomeUiState

    /** Has the Worker role but no worker profile yet. */
    data object NeedsProfile : WorkerHomeUiState

    /**
     * @property requests New requests waiting for an answer, soonest first.
     * @property active On the way or in progress.
     * @property upcoming Accepted, not started yet.
     * @property busyJobIds Jobs with an action in flight (buttons show progress).
     */
    data class Ready(
        val profile: WorkerProfile,
        val isUpdatingAvailability: Boolean,
        val requests: List<Job> = emptyList(),
        val active: List<Job> = emptyList(),
        val upcoming: List<Job> = emptyList(),
        val today: LocalDate,
        val busyJobIds: Set<String> = emptySet(),
    ) : WorkerHomeUiState
}

sealed interface WorkerHomeEvent {
    data class Failed(val error: AppError) : WorkerHomeEvent
}

/** The worker's main tab: verification progress, the "Accepting jobs" switch and their jobs. */
@HiltViewModel
class WorkerHomeViewModel @Inject constructor(
    private val workerProfileRepository: WorkerProfileRepository,
    private val jobRepository: JobRepository,
    private val clock: Clock,
) : ViewModel() {

    private val loadError = MutableStateFlow<AppError?>(null)
    private val updatingAvailability = MutableStateFlow(false)
    private val busyJobIds = MutableStateFlow<Set<String>>(emptySet())

    private val events = Channel<WorkerHomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<WorkerHomeEvent> = events.receiveAsFlow()

    val uiState: StateFlow<WorkerHomeUiState> = combine(
        workerProfileRepository.profile,
        jobRepository.jobs,
        loadError,
        updatingAvailability,
        busyJobIds,
    ) { profile, jobs, error, updating, busy ->
        when (profile) {
            WorkerProfileState.NotLoaded -> if (error != null) WorkerHomeUiState.Error(error) else WorkerHomeUiState.Loading
            WorkerProfileState.NotSetUp -> WorkerHomeUiState.NeedsProfile
            is WorkerProfileState.Ready -> WorkerHomeUiState.Ready(
                profile = profile.profile,
                isUpdatingAvailability = updating,
                requests = jobs.filter { it.status == BookingStatus.Requested },
                active = jobs.filter { it.status == BookingStatus.WorkerEnRoute || it.status == BookingStatus.InProgress },
                upcoming = jobs.filter { it.status == BookingStatus.Accepted },
                today = LocalDate.now(clock),
                busyJobIds = busy,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerHomeUiState.Loading)

    init {
        refresh()
    }

    fun onRetry() = refresh()

    /** Called periodically while the tab is visible: new requests, the verification decision. */
    fun onAutoRefresh() = refresh()

    fun onAccept(jobId: String) {
        if (jobId in busyJobIds.value) return
        busyJobIds.update { it + jobId }
        viewModelScope.launch {
            val result = jobRepository.accept(jobId)
            busyJobIds.update { it - jobId }
            if (result is AppResult.Failure) {
                events.send(WorkerHomeEvent.Failed(result.error))
                // Usually means the customer cancelled meanwhile: show the current state.
                jobRepository.refresh()
            }
        }
    }

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
            // Only verified workers can have jobs.
            val profile = (workerProfileRepository.profile.value as? WorkerProfileState.Ready)?.profile
            if (profile?.verificationStatus == WorkerVerificationStatus.Verified) jobRepository.refresh()
        }
    }
}
