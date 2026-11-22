package com.sahyog.feature.worker.verification

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.WorkerProfileRepository
import com.sahyog.core.model.IdDocumentType
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

data class IdVerificationUiState(
    val documentType: IdDocumentType? = null,
    val hasPhoto: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = documentType != null && hasPhoto && !isSubmitting
}

sealed interface IdVerificationEvent {
    data object Submitted : IdVerificationEvent

    data class Failed(val error: AppError) : IdVerificationEvent
}

/** Picks an ID type and a photo of it, and sends it for the cooperative's review. */
@HiltViewModel
class IdVerificationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workerProfileRepository: WorkerProfileRepository,
) : ViewModel() {

    // Kept in SavedStateHandle so rotation doesn't lose the choice. If the read grant has
    // expired (e.g. after process death), submitting fails with "pick the photo again".
    private val documentType = savedStateHandle.getStateFlow<String?>(KEY_TYPE, null)
    private val photoUri = savedStateHandle.getStateFlow<String?>(KEY_PHOTO, null)
    private val submitting = MutableStateFlow(false)

    private val events = Channel<IdVerificationEvent>(Channel.BUFFERED)
    val eventFlow: Flow<IdVerificationEvent> = events.receiveAsFlow()

    val uiState: StateFlow<IdVerificationUiState> = combine(documentType, photoUri, submitting) { type, photo, busy ->
        IdVerificationUiState(
            documentType = IdDocumentType.entries.firstOrNull { it.name == type },
            hasPhoto = photo != null,
            isSubmitting = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdVerificationUiState())

    fun onTypeSelect(type: IdDocumentType) {
        savedStateHandle[KEY_TYPE] = type.name
    }

    /** @param uri A content URI from the Photo Picker, or null if the worker backed out. */
    fun onPhotoPicked(uri: String?) {
        if (uri != null) savedStateHandle[KEY_PHOTO] = uri
    }

    fun onSubmit() {
        val type = IdDocumentType.entries.firstOrNull { it.name == documentType.value } ?: return
        val photo = photoUri.value ?: return
        if (submitting.value) return
        submitting.value = true
        viewModelScope.launch {
            val result = workerProfileRepository.submitIdDocument(type, photo)
            submitting.value = false
            events.send(
                when (result) {
                    is AppResult.Success -> IdVerificationEvent.Submitted
                    is AppResult.Failure -> IdVerificationEvent.Failed(result.error)
                },
            )
        }
    }

    private companion object {
        const val KEY_TYPE = "document_type"
        const val KEY_PHOTO = "photo_uri"
    }
}
