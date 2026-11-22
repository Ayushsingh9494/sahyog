package com.sahyog.feature.onboarding

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestAuthRepository
import com.sahyog.core.testing.TestUserRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val users = TestUserRepository()
    private val auth = TestAuthRepository()
    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { OnboardingViewModel(users, auth) }

    private val savedUser = User("u1", "9876543210", "Asha", setOf(UserRole.Customer), workerStatus = null)

    @Test
    fun `cannot submit without a valid name and at least one role`() {
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onNameChange("Asha")
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onRoleToggle(UserRole.Customer, true)
        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onRoleToggle(UserRole.Customer, false)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `invalid name only shows an error after submitting`() {
        viewModel.onNameChange("A1")
        assertFalse(viewModel.uiState.value.showNameError)

        viewModel.onSubmit()
        assertTrue(viewModel.uiState.value.showNameError)
        assertTrue(users.onboardingCalls.isEmpty())
    }

    @Test
    fun `submits trimmed name with both roles`() {
        users.onboardingResult = AppResult.Success(savedUser)
        viewModel.onNameChange("  Asha Verma ")
        viewModel.onRoleToggle(UserRole.Customer, true)
        viewModel.onRoleToggle(UserRole.Worker, true)

        viewModel.onSubmit()

        assertEquals(listOf("Asha Verma" to setOf(UserRole.Customer, UserRole.Worker)), users.onboardingCalls)
    }

    @Test
    fun `failure re-enables the form and shows the error`() {
        users.onboardingResult = AppResult.Failure(AppError.Network)
        viewModel.onNameChange("Asha")
        viewModel.onRoleToggle(UserRole.Worker, true)

        viewModel.onSubmit()

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitting)
        assertEquals(com.sahyog.core.ui.R.string.core_ui_error_network, state.errorRes)
    }

    @Test
    fun `use a different number signs out`() {
        viewModel.onUseDifferentNumber()
        assertEquals(1, auth.signOutCount)
    }
}
