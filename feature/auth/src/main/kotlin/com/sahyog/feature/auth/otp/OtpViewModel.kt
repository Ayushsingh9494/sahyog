package com.sahyog.feature.auth.otp

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.validation.OtpValidator
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.auth.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtpUiState(
    val phoneNumber: String,
    val code: String = "",
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val resendSecondsLeft: Int = 0,
    val showDemoHint: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val infoRes: Int? = null,
) {
    val canResend: Boolean get() = resendSecondsLeft == 0 && !isResending && !isVerifying
}

@HiltViewModel(assistedFactory = OtpViewModel.Factory::class)
class OtpViewModel @AssistedInject constructor(
    @Assisted phoneNumber: String,
    private val authRepository: AuthRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(phoneNumber: String): OtpViewModel
    }

    private val _uiState = MutableStateFlow(
        OtpUiState(phoneNumber = phoneNumber, showDemoHint = authRepository.isDemoMode),
    )
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        // The previous screen just sent a code, so start the resend cooldown immediately.
        startResendCountdown()
    }

    fun onCodeChange(input: String) {
        if (_uiState.value.isVerifying) return
        val code = OtpValidator.sanitize(input)
        _uiState.update { it.copy(code = code, errorRes = null, infoRes = null) }
        if (OtpValidator.isValid(code)) verify()
    }

    fun verify() {
        val state = _uiState.value
        if (!OtpValidator.isValid(state.code) || state.isVerifying) return
        _uiState.update { it.copy(isVerifying = true, errorRes = null, infoRes = null) }
        viewModelScope.launch {
            when (val result = authRepository.verifyOtp(state.phoneNumber, state.code)) {
                // Success needs no navigation: the session changes and the app root swaps
                // this whole section out. isVerifying stays true so nothing flickers meanwhile.
                is AppResult.Success -> Unit
                is AppResult.Failure -> _uiState.update {
                    it.copy(isVerifying = false, code = "", errorRes = result.error.toOtpMessage())
                }
            }
        }
    }

    fun resend() {
        val state = _uiState.value
        if (!state.canResend) return
        _uiState.update { it.copy(isResending = true, errorRes = null, infoRes = null) }
        viewModelScope.launch {
            when (val result = authRepository.requestOtp(state.phoneNumber)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isResending = false, code = "", infoRes = R.string.auth_otp_resent) }
                    startResendCountdown()
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isResending = false, errorRes = result.error.messageRes())
                }
            }
        }
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 0) {
                _uiState.update { it.copy(resendSecondsLeft = seconds) }
                if (seconds > 0) delay(1_000)
            }
        }
    }

    private fun AppError.toOtpMessage(): Int =
        if (this is AppError.Validation) R.string.auth_otp_error_wrong_code else messageRes()

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 30
    }
}
