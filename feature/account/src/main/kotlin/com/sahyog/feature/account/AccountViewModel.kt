package com.sahyog.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.model.SessionState
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AccountUiState {
    data object Loading : AccountUiState

    data class Ready(
        val user: User,
        val activeRole: UserRole,
        val theme: ThemePreference,
    ) : AccountUiState {
        val canSwitchRole: Boolean get() = user.roles.size > 1
    }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountUiState> =
        combine(authRepository.currentUser, preferencesRepository.preferences) { user, prefs ->
            when (val session = SessionState.resolve(user, prefs)) {
                is SessionState.SignedIn -> AccountUiState.Ready(session.user, session.activeRole, prefs.theme)
                // This screen only exists inside a signed-in section; anything else is transient.
                else -> AccountUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState.Loading)

    fun onSwitchRole(role: UserRole) {
        viewModelScope.launch { preferencesRepository.setActiveRole(role) }
    }

    fun onThemeChange(theme: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }

    fun onSignOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
