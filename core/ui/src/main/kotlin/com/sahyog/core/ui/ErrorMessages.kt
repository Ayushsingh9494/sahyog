package com.sahyog.core.ui

import androidx.annotation.StringRes
import com.sahyog.core.common.result.AppError

/**
 * Generic, user-facing message for an [AppError]. Screens override specific cases
 * (e.g. a wrong OTP) with more helpful copy and fall back to this for the rest,
 * so no error ever reaches the user as a raw exception message.
 */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.core_ui_error_network
    AppError.Unauthorized -> R.string.core_ui_error_unauthorized
    AppError.Forbidden -> R.string.core_ui_error_forbidden
    AppError.NotFound -> R.string.core_ui_error_not_found
    AppError.RateLimited -> R.string.core_ui_error_rate_limited
    is AppError.Validation -> R.string.core_ui_error_validation
    is AppError.Server -> R.string.core_ui_error_server
    is AppError.Unknown -> R.string.core_ui_error_unknown
}
