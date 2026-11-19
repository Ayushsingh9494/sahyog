package com.sahyog.feature.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.validation.NameValidator
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.model.UserRole
import com.sahyog.core.ui.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val name: String = "",
    val roles: Set<UserRole> = emptySet(),
    val isSubmitting: Boolean = false,
    /** Name errors are shown only after the first submit attempt, not while typing. */
    val showNameError: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
    val isNameValid: Boolean get() = NameValidator.isValid(name)
    val canSubmit: Boolean get() = isNameValid && roles.isNotEmpty() && !isSubmitting
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(NameValidator.MAX_LENGTH), errorRes = null) }
    }

    fun onRoleToggle(role: UserRole, selected: Boolean) {
        _uiState.update { it.copy(roles = if (selected) it.roles + role else it.roles - role, errorRes = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.isNameValid) {
            _uiState.update { it.copy(showNameError = true) }
            return
        }
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, errorRes = null) }
        viewModelScope.launch {
            when (val result = userRepository.completeOnboarding(state.name.trim(), state.roles)) {
                // Success: the session becomes SignedIn and the app root shows the right home.
                is AppResult.Success -> Unit
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorRes = result.error.toMessage())
                }
            }
        }
    }

    /** Lets someone who typed the wrong number start over. */
    fun onUseDifferentNumber() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun AppError.toMessage(): Int = when (this) {
        is AppError.Validation -> if (field == "name") R.string.onboarding_name_error else messageRes()
        else -> messageRes()
    }
}
