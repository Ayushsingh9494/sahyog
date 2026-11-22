package com.sahyog.core.common.result

/**
 * Every failure the app can surface, independent of where it came from
 * (Retrofit, Room, Firebase). The data layer maps raw exceptions into one of
 * these exactly once; the UI layer maps them to user-facing strings.
 */
sealed interface AppError {
    /** No connectivity or a timeout. Usually retryable. */
    data object Network : AppError

    /** Session expired or missing. The app should route back to login. */
    data object Unauthorized : AppError

    /** The user lacks the role/permission for this action. */
    data object Forbidden : AppError

    data object NotFound : AppError

    /** Too many attempts (e.g. OTP requests). The user must wait before retrying. */
    data object RateLimited : AppError

    /** Server rejected input. [field] lets forms highlight the offending input. */
    data class Validation(val field: String? = null, val message: String? = null) : AppError

    data class Server(val code: Int) : AppError

    data class Unknown(val cause: Throwable? = null) : AppError
}
