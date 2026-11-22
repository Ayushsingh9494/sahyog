package com.sahyog.feature.worker.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.JobRepository
import com.sahyog.core.model.Job
import com.sahyog.core.model.JobAction
import com.sahyog.core.model.allowedJobActions
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import java.time.Clock
import java.time.LocalDate

sealed interface JobDetailUiState {
    data object Loading : JobDetailUiState

    data object NotFound : JobDetailUiState

    /** @property actionInFlight The action being sent to the server, if any. */
    data class Ready(
        val job: Job,
        val today: LocalDate,
        val actionInFlight: JobAction?,
    ) : JobDetailUiState {
        val actions: Set<JobAction> get() = job.status.allowedJobActions()
    }
}

sealed interface JobDetailEvent {
    data class Done(val action: JobAction) : JobDetailEvent

    data class Failed(val action: JobAction, val error: AppError) : JobDetailEvent
}

@HiltViewModel(assistedFactory = JobDetailViewModel.Factory::class)
class JobDetailViewModel @AssistedInject constructor(
    @Assisted private val jobId: String,
    private val jobRepository: JobRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): JobDetailViewModel
    }

    private val inFlight = MutableStateFlow<JobAction?>(null)

    private val events = Channel<JobDetailEvent>(Channel.BUFFERED)
    val eventFlow: Flow<JobDetailEvent> = events.receiveAsFlow()

    val uiState: StateFlow<JobDetailUiState> = combine(jobRepository.job(jobId), inFlight) { job, action ->
        if (job == null) JobDetailUiState.NotFound else JobDetailUiState.Ready(job, LocalDate.now(clock), action)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobDetailUiState.Loading)

    init {
        viewModelScope.launch { jobRepository.refresh() }
    }

    /** Re-checks the job (e.g. whether the customer cancelled) while the screen is visible. */
    fun onAutoRefresh() {
        if (inFlight.value == null) viewModelScope.launch { jobRepository.refresh() }
    }

    fun onAccept() = perform(JobAction.Accept) { jobRepository.accept(jobId) }

    fun onDecline() = perform(JobAction.Decline) { jobRepository.decline(jobId) }

    fun onStartTrip() = perform(JobAction.StartTrip) { jobRepository.startTrip(jobId) }

    fun onStartJob(code: String) = perform(JobAction.StartJob) { jobRepository.startJob(jobId, code) }

    fun onComplete() = perform(JobAction.Complete) { jobRepository.complete(jobId) }

    private fun perform(action: JobAction, call: suspend () -> AppResult<Job>) {
        if (inFlight.value != null) return
        inFlight.value = action
        viewModelScope.launch {
            val result = call()
            inFlight.value = null
            when (result) {
                is AppResult.Success -> events.send(JobDetailEvent.Done(action))
                is AppResult.Failure -> {
                    events.send(JobDetailEvent.Failed(action, result.error))
                    jobRepository.refresh()
                }
            }
        }
    }
}
