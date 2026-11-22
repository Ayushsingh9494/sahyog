package com.sahyog.core.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.sahyog.core.common.result.AppError

/** Validation field names shared with the auth screens. */
object AuthErrorFields {
    const val PHONE = "phone"
    const val OTP = "otp"

    /** The code was right format-wise but the verification session has expired. */
    const val OTP_EXPIRED = "otp_expired"
}

/**
 * Translates Firebase exceptions into [AppError] in exactly one place, so no Firebase
 * type ever reaches a ViewModel. Firebase reports most auth failures through error codes.
 */
internal fun Throwable.toAuthAppError(): AppError = when (this) {
    is FirebaseNetworkException -> AppError.Network
    is FirebaseTooManyRequestsException -> AppError.RateLimited
    is FirebaseAuthInvalidCredentialsException -> when (errorCode) {
        "ERROR_INVALID_PHONE_NUMBER", "ERROR_MISSING_PHONE_NUMBER" -> AppError.Validation(AuthErrorFields.PHONE)
        "ERROR_SESSION_EXPIRED" -> AppError.Validation(AuthErrorFields.OTP_EXPIRED)
        else -> AppError.Validation(AuthErrorFields.OTP) // ERROR_INVALID_VERIFICATION_CODE and similar
    }
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_QUOTA_EXCEEDED", "ERROR_TOO_MANY_REQUESTS" -> AppError.RateLimited
        "ERROR_SESSION_EXPIRED" -> AppError.Validation(AuthErrorFields.OTP_EXPIRED)
        "ERROR_USER_DISABLED" -> AppError.Forbidden
        else -> AppError.Unknown(this)
    }
    else -> AppError.Unknown(this)
}
