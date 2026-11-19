package com.sahyog.core.data.repository

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Phone-number authentication.
 * Real implementation (Milestone 4): Firebase Auth phone OTP.
 */
interface AuthRepository {
    /** The signed-in user, or null. Emits on every sign-in, profile change and sign-out. */
    val currentUser: Flow<User?>

    /** True when backed by the local fake. Screens may show a demo hint. Never true in production. */
    val isDemoMode: Boolean get() = false

    /** Sends a one-time code to [phoneNumber] (10 digits, no country code). */
    suspend fun requestOtp(phoneNumber: String): AppResult<Unit>

    suspend fun verifyOtp(phoneNumber: String, code: String): AppResult<User>

    suspend fun signOut()
}

/**
 * Profile data owned by the Sahyog backend.
 * Real implementation (Milestone 10): Spring Boot REST API.
 */
interface UserRepository {
    /** Sets the user's display name and requested roles. Workers start as NotSubmitted. */
    suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User>
}

/** Device-local preferences. Always a real (DataStore-backed) implementation. */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setActiveRole(role: UserRole)
}
