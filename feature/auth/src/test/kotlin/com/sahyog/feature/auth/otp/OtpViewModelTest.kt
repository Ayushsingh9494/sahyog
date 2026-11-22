package com.sahyog.feature.auth.otp

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.auth.AuthErrorFields
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestAuthRepository
import com.sahyog.feature.auth.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpViewModelTest {

    // StandardTestDispatcher: coroutines only advance when the test says so,
    // which lets us step through the 30 s countdown in virtual time.
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val auth = TestAuthRepository()

    private fun createViewModel() = OtpViewModel(PHONE, auth)

    @Test
    fun `entering the sixth digit verifies automatically`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onCodeChange("12345")
        runCurrent()
        assertTrue(auth.verifiedCodes.isEmpty())

        viewModel.onCodeChange("123456")
        runCurrent()
        assertEquals(listOf("123456"), auth.verifiedCodes)
        // Stays in the verifying state; the app root replaces this screen on success.
        assertTrue(viewModel.uiState.value.isVerifying)
    }

    @Test
    fun `wrong code clears the input and shows an error`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.verifyOtpResult = AppResult.Failure(AppError.Validation(AuthErrorFields.OTP))
        val viewModel = createViewModel()

        viewModel.onCodeChange("000000")
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isVerifying)
        assertEquals("", state.code)
        assertEquals(R.string.auth_otp_error_wrong_code, state.errorRes)
    }

    @Test
    fun `resend is locked for 30 seconds then available`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(30, viewModel.uiState.value.resendSecondsLeft)
        assertFalse(viewModel.uiState.value.canResend)

        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.resendSecondsLeft)

        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(viewModel.uiState.value.canResend)
    }

    @Test
    fun `resend requests a new code and restarts the countdown`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(30_000)
        runCurrent()

        viewModel.resend()
        runCurrent()

        // resend = true reuses Firebase's resend token for the same number.
        assertEquals(listOf(PHONE to true), auth.otpRequests)
        assertEquals(R.string.auth_otp_resent, viewModel.uiState.value.infoRes)
        assertEquals(30, viewModel.uiState.value.resendSecondsLeft)
    }

    @Test
    fun `expired verification asks the user to request a new code`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.verifyOtpResult = AppResult.Failure(AppError.Validation(AuthErrorFields.OTP_EXPIRED))
        val viewModel = createViewModel()

        viewModel.onCodeChange("123456")
        runCurrent()

        assertEquals(R.string.auth_otp_error_expired, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `rate limiting shows the generic too-many-attempts message`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.verifyOtpResult = AppResult.Failure(AppError.RateLimited)
        val viewModel = createViewModel()

        viewModel.onCodeChange("123456")
        runCurrent()

        assertEquals(com.sahyog.core.ui.R.string.core_ui_error_rate_limited, viewModel.uiState.value.errorRes)
    }

    private companion object {
        const val PHONE = "9876543210"
    }
}
