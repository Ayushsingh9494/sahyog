package com.sahyog.feature.admin.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AdminRepository
import com.sahyog.core.model.AdminWorkerDetail
import com.sahyog.core.model.WorkerVerificationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** The ID photo: fetched only when the screen opens, held in memory, never saved. */
sealed interface DocumentState {
    data object None : DocumentState

    data object Loading : DocumentState

    class Loaded(val bytes: ByteArray) : DocumentState

    data class Failed(val error: AppError) : DocumentState
}

/** What the admin may do with this worker right now. */
enum class AdminAction { Approve, Reject, Revoke }

sealed interface AdminWorkerUiState {
    data object Loading : AdminWorkerUiState

    data class Error(val error: AppError) : AdminWorkerUiState

    data class Ready(
        val detail: AdminWorkerDetail,
        val document: DocumentState,
        val now: Instant,
        /** The action in flight, if any (its button shows progress, the others are disabled). */
        val acting: AdminAction? = null,
    ) : AdminWorkerUiState {
        val actions: List<AdminAction>
            get() = when (detail.worker.status) {
                WorkerVerificationStatus.Pending -> listOf(AdminAction.Reject, AdminAction.Approve)
                WorkerVerificationStatus.Verified -> listOf(AdminAction.Revoke)
                else -> emptyList()
            }
    }
}

sealed interface AdminWorkerEvent {
    /** Decided; the screen confirms and goes back. */
    data class Done(val action: AdminAction, val workerName: String) : AdminWorkerEvent

    data class Failed(val error: AppError) : AdminWorkerEvent
}

@HiltViewModel(assistedFactory = AdminWorkerViewModel.Factory::class)
class AdminWorkerViewModel @AssistedInject constructor(
    @Assisted private val workerId: String,
    private val adminRepository: AdminRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(workerId: String): AdminWorkerViewModel
    }

    private val state = MutableStateFlow<AdminWorkerUiState>(AdminWorkerUiState.Loading)
    val uiState: StateFlow<AdminWorkerUiState> = state.asStateFlow()

    private val events = Channel<AdminWorkerEvent>(Channel.BUFFERED)
    val eventFlow: Flow<AdminWorkerEvent> = events.receiveAsFlow()

    init {
        load()
    }

    fun onRetry() = load()

    fun onRetryDocument() = loadDocument()

    fun onApprove() = act(AdminAction.Approve) { adminRepository.approve(workerId) }

    fun onReject(reason: String) = act(AdminAction.Reject) { adminRepository.reject(workerId, reason) }

    fun onRevoke(reason: String) = act(AdminAction.Revoke) { adminRepository.revoke(workerId, reason) }

    private fun load() {
        state.value = AdminWorkerUiState.Loading
        viewModelScope.launch {
            state.value = when (val result = adminRepository.worker(workerId)) {
                is AppResult.Success -> AdminWorkerUiState.Ready(
                    detail = result.data,
                    document = if (result.data.documentType != null) DocumentState.Loading else DocumentState.None,
                    now = clock.instant(),
                )
                is AppResult.Failure -> AdminWorkerUiState.Error(result.error)
            }
            if ((state.value as? AdminWorkerUiState.Ready)?.document == DocumentState.Loading) loadDocument()
        }
    }

    private fun loadDocument() {
        setDocument(DocumentState.Loading)
        viewModelScope.launch {
            setDocument(
                when (val result = adminRepository.document(workerId)) {
                    is AppResult.Success -> DocumentState.Loaded(result.data)
                    is AppResult.Failure -> DocumentState.Failed(result.error)
                },
            )
        }
    }

    private fun setDocument(document: DocumentState) =
        state.update { (it as? AdminWorkerUiState.Ready)?.copy(document = document) ?: it }

    private fun act(action: AdminAction, call: suspend () -> AppResult<Unit>) {
        val ready = state.value as? AdminWorkerUiState.Ready ?: return
        if (ready.acting != null || action !in ready.actions) return
        state.value = ready.copy(acting = action)
        viewModelScope.launch {
            val result = call()
            state.update { (it as? AdminWorkerUiState.Ready)?.copy(acting = null) ?: it }
            when (result) {
                is AppResult.Success -> events.send(AdminWorkerEvent.Done(action, ready.detail.worker.name))
                is AppResult.Failure -> {
                    events.send(AdminWorkerEvent.Failed(result.error))
                    // e.g. another admin decided first: show the current state.
                    if (result.error is AppError.Validation) load()
                }
            }
        }
    }
}
