package com.sahyog.core.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.sahyog.core.common.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseErrorsTest {

    @Test
    fun `network and rate limit errors map to their own cases`() {
        assertEquals(AppError.Network, FirebaseNetworkException("offline").toAuthAppError())
        assertEquals(AppError.RateLimited, FirebaseTooManyRequestsException("slow down").toAuthAppError())
        assertEquals(AppError.RateLimited, FirebaseAuthException("ERROR_QUOTA_EXCEEDED", "quota").toAuthAppError())
    }

    @Test
    fun `invalid credentials map to the field the user must fix`() {
        assertEquals(
            AppError.Validation(AuthErrorFields.PHONE),
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_PHONE_NUMBER", "bad").toAuthAppError(),
        )
        assertEquals(
            AppError.Validation(AuthErrorFields.OTP),
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "bad").toAuthAppError(),
        )
        assertEquals(
            AppError.Validation(AuthErrorFields.OTP_EXPIRED),
            FirebaseAuthInvalidCredentialsException("ERROR_SESSION_EXPIRED", "old").toAuthAppError(),
        )
    }

    @Test
    fun `disabled accounts are forbidden and anything else is unknown`() {
        assertEquals(AppError.Forbidden, FirebaseAuthException("ERROR_USER_DISABLED", "x").toAuthAppError())
        assertTrue(IllegalStateException("boom").toAuthAppError() is AppError.Unknown)
    }
}
