package com.sahyog.core.data.repository

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import kotlinx.coroutines.flow.Flow

/** Outcome of asking for a verification code. */
sealed interface OtpRequestResult {
    /** An SMS code was sent; the user must type it in. */
    data object CodeSent : OtpRequestResult

    /**
     * The device was verified without a code (instant verification) and the user is
     * already signed in. No OTP screen is needed: the session change moves the app on.
     */
    data object SignedIn : OtpRequestResult
}

/**
 * Phone-number authentication: proves WHO the user is. Knows nothing about profiles.
 * Implementation: Firebase Auth (phone OTP).
 */
interface AuthRepository {
    /** The verified identity, or null when signed out. */
    val session: Flow<AuthSession?>

    /**
     * Sends a one-time code to [phoneNumber] (10 digits, no country code).
     * @param resend Re-send to the same number, reusing the previous request.
     */
    suspend fun requestOtp(phoneNumber: String, resend: Boolean = false): AppResult<OtpRequestResult>

    /** Checks [code] for the number most recently passed to [requestOtp]. */
    suspend fun verifyOtp(phoneNumber: String, code: String): AppResult<Unit>

    suspend fun signOut()
}

/**
 * The signed-in person's profile (name, roles, verification), combined with their identity.
 * Implementation: local stand-in until the Spring Boot API arrives (Milestone 10).
 */
interface UserRepository {
    /** The signed-in user with their profile, or null when signed out. */
    val currentUser: Flow<User?>

    /** Sets the display name and requested roles. Workers start as NotSubmitted. */
    suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User>
}

/** Device-local preferences. Always a real (DataStore-backed) implementation. */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setActiveRole(role: UserRole)
}
