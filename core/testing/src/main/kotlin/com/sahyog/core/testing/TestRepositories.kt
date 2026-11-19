package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.UserPreferencesRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/*
 * Controllable, instant test doubles. Tests set the next result and inspect the calls,
 * with no delays or DataStore involved.
 */

class TestAuthRepository : AuthRepository {
    private val user = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = user

    var requestOtpResult: AppResult<Unit> = AppResult.Success(Unit)
    var verifyOtpResult: AppResult<User>? = null
    val requestedPhones = mutableListOf<String>()
    val verifiedCodes = mutableListOf<String>()
    var signOutCount = 0
        private set

    fun setUser(value: User?) = user.update { value }

    override suspend fun requestOtp(phoneNumber: String): AppResult<Unit> {
        requestedPhones += phoneNumber
        return requestOtpResult
    }

    override suspend fun verifyOtp(phoneNumber: String, code: String): AppResult<User> {
        verifiedCodes += code
        val result = checkNotNull(verifyOtpResult) { "Set verifyOtpResult before calling verifyOtp" }
        if (result is AppResult.Success) setUser(result.data)
        return result
    }

    override suspend fun signOut() {
        signOutCount++
        setUser(null)
    }
}

class TestUserRepository : UserRepository {
    var result: AppResult<User>? = null
    val calls = mutableListOf<Pair<String, Set<UserRole>>>()

    override suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User> {
        calls += name to roles
        return checkNotNull(result) { "Set result before calling completeOnboarding" }
    }
}

class TestUserPreferencesRepository : UserPreferencesRepository {
    private val prefs = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = prefs

    override suspend fun setTheme(theme: ThemePreference) = prefs.update { it.copy(theme = theme) }

    override suspend fun setActiveRole(role: UserRole) = prefs.update { it.copy(activeRole = role) }
}
