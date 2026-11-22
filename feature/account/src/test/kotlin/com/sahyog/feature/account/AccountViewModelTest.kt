package com.sahyog.feature.account

import app.cash.turbine.test
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestAuthRepository
import com.sahyog.core.testing.TestUserPreferencesRepository
import com.sahyog.core.testing.TestUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val users = TestUserRepository()
    private val auth = TestAuthRepository()
    private val prefs = TestUserPreferencesRepository()
    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { AccountViewModel(users, auth, prefs) }

    private val bothRoles = User(
        id = "u1",
        phoneNumber = "9876543210",
        name = "Asha",
        roles = setOf(UserRole.Customer, UserRole.Worker),
        workerStatus = WorkerVerificationStatus.Pending,
    )

    @Test
    fun `ready state reflects user, active role and theme`() = runTest {
        users.setUser(bothRoles)
        viewModel.uiState.test {
            val ready = awaitReady()
            assertEquals(UserRole.Customer, ready.activeRole)
            assertEquals(ThemePreference.System, ready.theme)
            assertTrue(ready.canSwitchRole)
        }
    }

    @Test
    fun `switching role and theme updates preferences`() = runTest {
        users.setUser(bothRoles)
        viewModel.uiState.test {
            awaitReady()
            viewModel.onSwitchRole(UserRole.Worker)
            assertEquals(UserRole.Worker, awaitReady().activeRole)
            viewModel.onThemeChange(ThemePreference.Dark)
            assertEquals(ThemePreference.Dark, awaitReady().theme)
        }
    }

    @Test
    fun `single role users cannot switch`() = runTest {
        users.setUser(bothRoles.copy(roles = setOf(UserRole.Customer), workerStatus = null))
        viewModel.uiState.test {
            assertFalse(awaitReady().canSwitchRole)
        }
    }

    @Test
    fun `sign out delegates to auth`() {
        viewModel.onSignOut()
        assertEquals(1, auth.signOutCount)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<AccountUiState>.awaitReady(): AccountUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is AccountUiState.Ready) return item
        }
    }
}
