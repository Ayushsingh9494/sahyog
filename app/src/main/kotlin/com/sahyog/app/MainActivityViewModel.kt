package com.sahyog.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.model.SessionState
import com.sahyog.core.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainActivityUiState {
    /** Session not read from disk yet. The splash screen stays up during this state. */
    data object Loading : MainActivityUiState

    data class Ready(val session: SessionState, val theme: ThemePreference) : MainActivityUiState
}

/**
 * App-wide state: who is signed in, in which mode, and with which theme.
 * Everything the root UI needs to decide what to show comes from this one flow.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<MainActivityUiState> =
        combine(authRepository.currentUser, preferencesRepository.preferences) { user, prefs ->
            MainActivityUiState.Ready(
                session = SessionState.resolve(user, prefs),
                theme = prefs.theme,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainActivityUiState.Loading)

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }
}
