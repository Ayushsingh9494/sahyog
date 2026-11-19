package com.sahyog.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sahyog.app.ui.shell.CustomerShell
import com.sahyog.app.ui.shell.WorkerShell
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.SessionState
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.UserRole
import com.sahyog.feature.auth.navigation.AuthNavHost
import com.sahyog.feature.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

@Serializable private data object OnboardingRoute

/**
 * Root of the UI. Renders exactly one section for the current [SessionState].
 *
 * Sections are switched by state, not by navigate() calls: signing in, completing
 * onboarding, switching mode and signing out all just change the session, and the
 * matching section replaces the previous one together with its whole back stack.
 * That makes "Back after logout returns to a signed-in screen" impossible by design.
 */
@Composable
fun SahyogApp(
    session: SessionState,
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = SahyogTheme.colors.surface) {
        AnimatedContent(
            targetState = session,
            // Same key = same section instance (e.g. profile updates don't reset the shell).
            contentKey = { it.sectionKey() },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
            label = "app-section",
        ) { state ->
            SectionViewModelScope(key = state.sectionKey()) {
                when (state) {
                    SessionState.SignedOut -> AuthNavHost()
                    is SessionState.NeedsOnboarding -> {
                        // A one-destination NavHost so onboarding gets standard lifecycle and
                        // saved-state handling, and can grow into multiple steps (worker skills, M7).
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = OnboardingRoute) {
                            composable<OnboardingRoute> { OnboardingScreen() }
                        }
                    }
                    is SessionState.SignedIn -> when (state.activeRole) {
                        UserRole.Customer -> CustomerShell(
                            user = state.user,
                            darkTheme = darkTheme,
                            onThemeChange = onThemeChange,
                            showDeveloperOptions = showDeveloperOptions,
                        )
                        UserRole.Worker -> WorkerShell(
                            user = state.user,
                            darkTheme = darkTheme,
                            onThemeChange = onThemeChange,
                            showDeveloperOptions = showDeveloperOptions,
                        )
                    }
                }
            }
        }
    }
}

private fun SessionState.sectionKey(): String = when (this) {
    SessionState.SignedOut -> "auth"
    is SessionState.NeedsOnboarding -> "onboarding"
    // Include the user id so a different account never inherits another's screens.
    is SessionState.SignedIn -> "${activeRole.name.lowercase()}:${user.id}"
}
