package com.sahyog.feature.auth.phone

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.validation.PhoneNumberValidator
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.OtpRequestResult
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.auth.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneEntryUiState(
    val phoneNumber: String = "",
    val isSubmitting: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
    /** A complete 10-digit number that isn't a valid mobile number (e.g. starts with 1-5). */
    val isInvalidComplete: Boolean
        get() = phoneNumber.length == PhoneNumberValidator.LENGTH && !PhoneNumberValidator.isValid(phoneNumber)

    val canSubmit: Boolean
        get() = PhoneNumberValidator.isValid(phoneNumber) && !isSubmitting
}

sealed interface PhoneEntryEvent {
    data class OtpSent(val phoneNumber: String) : PhoneEntryEvent
}

@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneEntryUiState())
    val uiState: StateFlow<PhoneEntryUiState> = _uiState.asStateFlow()

    private val _events = Channel<PhoneEntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onPhoneNumberChange(input: String) {
        _uiState.update { it.copy(phoneNumber = PhoneNumberValidator.sanitize(input), errorRes = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, errorRes = null) }
        viewModelScope.launch {
            when (val result = authRepository.requestOtp(state.phoneNumber)) {
                is AppResult.Success -> when (result.data) {
                    OtpRequestResult.CodeSent -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        _events.send(PhoneEntryEvent.OtpSent(state.phoneNumber))
                    }
                    // Verified without a code: the session changes and the app root moves on.
                    // Stay in the submitting state so nothing flickers meanwhile.
                    OtpRequestResult.SignedIn -> Unit
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorRes = result.error.toPhoneMessage())
                }
            }
        }
    }

    private fun AppError.toPhoneMessage(): Int =
        if (this is AppError.Validation) R.string.auth_phone_error_invalid else messageRes()
}
