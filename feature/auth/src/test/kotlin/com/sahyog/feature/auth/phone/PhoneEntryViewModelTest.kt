package com.sahyog.feature.auth.phone

import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestAuthRepository
import com.sahyog.feature.auth.R
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneEntryViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val auth = TestAuthRepository()
    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { PhoneEntryViewModel(auth) }

    @Test
    fun `input is sanitised to ten digits`() {
        viewModel.onPhoneNumberChange("+91 98765 43210")
        assertEquals("9876543210", viewModel.uiState.value.phoneNumber)
    }

    @Test
    fun `incomplete or invalid numbers cannot be submitted`() {
        viewModel.onPhoneNumberChange("98765")
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onPhoneNumberChange("1234567890")
        assertTrue(viewModel.uiState.value.isInvalidComplete)
        viewModel.onSubmit()
        assertTrue(auth.requestedPhones.isEmpty())
    }

    @Test
    fun `successful request emits OtpSent with the number`() = runTest {
        viewModel.onPhoneNumberChange("9876543210")
        viewModel.events.test {
            viewModel.onSubmit()
            assertEquals(PhoneEntryEvent.OtpSent("9876543210"), awaitItem())
        }
        assertEquals(listOf("9876543210"), auth.requestedPhones)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `network failure shows the generic network message`() {
        auth.requestOtpResult = AppResult.Failure(AppError.Network)
        viewModel.onPhoneNumberChange("9876543210")
        viewModel.onSubmit()
        assertEquals(com.sahyog.core.ui.R.string.core_ui_error_network, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `validation failure shows the phone specific message and editing clears it`() {
        auth.requestOtpResult = AppResult.Failure(AppError.Validation("phone"))
        viewModel.onPhoneNumberChange("9876543210")
        viewModel.onSubmit()
        assertEquals(R.string.auth_phone_error_invalid, viewModel.uiState.value.errorRes)

        viewModel.onPhoneNumberChange("987654321")
        assertEquals(null, viewModel.uiState.value.errorRes)
    }
}
