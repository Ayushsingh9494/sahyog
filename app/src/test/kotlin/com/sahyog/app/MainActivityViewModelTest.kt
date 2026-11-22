package com.sahyog.app

import app.cash.turbine.test
import com.sahyog.core.model.SessionState
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestUserPreferencesRepository
import com.sahyog.core.testing.TestUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val users = TestUserRepository()
    private val prefs = TestUserPreferencesRepository()
    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { MainActivityViewModel(users, prefs) }

    @Test
    fun `starts loading, then signed out`() = runTest {
        viewModel.uiState.test {
            // With an unconfined dispatcher the first real value may already be available.
            var item = awaitItem()
            if (item is MainActivityUiState.Loading) item = awaitItem()
            assertEquals(MainActivityUiState.Ready(SessionState.SignedOut, ThemePreference.System), item)
        }
    }

    @Test
    fun `session follows sign in, onboarding and sign out`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()

            val newUser = User("u1", "9876543210", name = null, roles = emptySet(), workerStatus = null)
            users.setUser(newUser)
            assertEquals(SessionState.NeedsOnboarding(newUser), awaitReady().session)

            val onboarded = newUser.copy(name = "Asha", roles = setOf(UserRole.Customer))
            users.setUser(onboarded)
            assertEquals(SessionState.SignedIn(onboarded, UserRole.Customer), awaitReady().session)

            users.setUser(null)
            assertEquals(SessionState.SignedOut, awaitReady().session)
        }
    }

    @Test
    fun `theme changes are persisted and exposed`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.setTheme(ThemePreference.Dark)
            assertEquals(ThemePreference.Dark, awaitReady().theme)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<MainActivityUiState>.awaitReady() =
        awaitItem() as MainActivityUiState.Ready

    private suspend fun app.cash.turbine.ReceiveTurbine<MainActivityUiState>.skipUntilReady() {
        while (awaitItem() !is MainActivityUiState.Ready) Unit
    }
}
