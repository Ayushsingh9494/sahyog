package com.sahyog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sahyog.app.R
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.theme.SahyogSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Retry or leave when the profile couldn't be loaded (and none is cached). */
@HiltViewModel
class ProfileUnavailableViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val retrying = MutableStateFlow(false)
    val isRetrying = retrying.asStateFlow()

    fun onRetry() {
        if (retrying.value) return
        retrying.value = true
        // On success the session moves on by itself (the profile appears); on failure we stay.
        viewModelScope.launch {
            userRepository.refresh()
            retrying.value = false
        }
    }

    fun onSignOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}

/** Signed in, but the profile couldn't be fetched (usually no internet, or the server is down). */
@Composable
internal fun ProfileUnavailableScreen(viewModel: ProfileUnavailableViewModel = hiltViewModel()) {
    val retrying by viewModel.isRetrying.collectAsStateWithLifecycle()
    if (retrying) {
        SahyogLoadingView(Modifier.fillMaxSize())
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SahyogSpacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SahyogErrorView(
            title = stringResource(R.string.app_profile_unavailable_title),
            message = stringResource(R.string.app_profile_unavailable_body),
            onRetry = viewModel::onRetry,
        )
        SahyogTextButton(text = stringResource(R.string.app_profile_unavailable_sign_out), onClick = viewModel::onSignOut)
    }
}
