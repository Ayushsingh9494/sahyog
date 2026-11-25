package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.OtpRequestResult
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.UserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/*
 * Controllable, instant test doubles. Tests set the next result and inspect the calls,
 * with no delays, Firebase or DataStore involved.
 */

class TestAuthRepository : AuthRepository {
    private val sessionFlow = MutableStateFlow<AuthSession?>(null)
    override val session: Flow<AuthSession?> = sessionFlow

    var requestOtpResult: AppResult<OtpRequestResult> = AppResult.Success(OtpRequestResult.CodeSent)
    var verifyOtpResult: AppResult<Unit> = AppResult.Success(Unit)

    /** (phone, resend) for every requestOtp call. */
    val otpRequests = mutableListOf<Pair<String, Boolean>>()
    val verifiedCodes = mutableListOf<String>()
    var signOutCount = 0
        private set

    fun setSession(value: AuthSession?) = sessionFlow.update { value }

    override suspend fun requestOtp(phoneNumber: String, resend: Boolean): AppResult<OtpRequestResult> {
        otpRequests += phoneNumber to resend
        return requestOtpResult
    }

    override suspend fun verifyOtp(phoneNumber: String, code: String): AppResult<Unit> {
        verifiedCodes += code
        return verifyOtpResult
    }

    override suspend fun signOut() {
        signOutCount++
        setSession(null)
    }
}

class TestUserRepository : UserRepository {
    private val user = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = user

    /** Signed out when there's no user, else Ready; override with [setState] for loading/unavailable. */
    private val stateOverride = MutableStateFlow<UserState?>(null)
    override val userState: Flow<UserState> = combine(user, stateOverride) { u, override ->
        override ?: if (u == null) UserState.SignedOut else UserState.Ready(u)
    }

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshCount = 0
        private set

    fun setState(value: UserState?) = stateOverride.update { value }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCount++
        return refreshResult
    }

    var onboardingResult: AppResult<User>? = null
    val onboardingCalls = mutableListOf<Pair<String, Set<UserRole>>>()

    fun setUser(value: User?) = user.update { value }

    override suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User> {
        onboardingCalls += name to roles
        val result = checkNotNull(onboardingResult) { "Set onboardingResult before calling completeOnboarding" }
        if (result is AppResult.Success) setUser(result.data)
        return result
    }
}

class TestUserPreferencesRepository : UserPreferencesRepository {
    private val prefs = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = prefs

    override suspend fun setTheme(theme: ThemePreference) = prefs.update { it.copy(theme = theme) }

    override suspend fun setActiveRole(role: UserRole) = prefs.update { it.copy(activeRole = role) }
}
