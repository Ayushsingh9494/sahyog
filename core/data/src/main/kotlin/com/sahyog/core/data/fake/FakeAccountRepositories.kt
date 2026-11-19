package com.sahyog.core.data.fake

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.validation.NameValidator
import com.sahyog.core.common.validation.OtpValidator
import com.sahyog.core.common.validation.PhoneNumberValidator
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.data.repository.UserRepository
import com.sahyog.core.datastore.SahyogPreferencesDataSource
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/*
 * Local stand-ins for Firebase Auth and the Sahyog backend, so the whole app flow
 * can be built and demoed before those exist. They validate input and add network-like
 * latency, so loading and error states are exercised. They persist to DataStore, so a
 * "session" survives app restarts.
 *
 * Replaced by real implementations in Milestones 4 and 10 (change the bindings in DataModule).
 */

internal const val DEMO_OTP = "123456"
private const val FAKE_LATENCY_MS = 700L

internal class FakeAuthRepository @Inject constructor(
    private val local: SahyogPreferencesDataSource,
) : AuthRepository {

    override val currentUser: Flow<User?> = local.cachedUser

    override val isDemoMode: Boolean = true

    override suspend fun requestOtp(phoneNumber: String): AppResult<Unit> {
        delay(FAKE_LATENCY_MS)
        if (!PhoneNumberValidator.isValid(phoneNumber)) {
            return AppResult.Failure(AppError.Validation(field = "phone"))
        }
        return AppResult.Success(Unit)
    }

    override suspend fun verifyOtp(phoneNumber: String, code: String): AppResult<User> {
        delay(FAKE_LATENCY_MS)
        if (!OtpValidator.isValid(code) || code != DEMO_OTP) {
            return AppResult.Failure(AppError.Validation(field = "otp"))
        }
        // The fake has no server-side memory: every sign-in starts a fresh profile.
        val user = User(
            id = "demo-$phoneNumber",
            phoneNumber = phoneNumber,
            name = null,
            roles = emptySet(),
            workerStatus = null,
        )
        local.saveUser(user)
        return AppResult.Success(user)
    }

    override suspend fun signOut() {
        local.clearSession()
    }
}

internal class FakeUserRepository @Inject constructor(
    private val local: SahyogPreferencesDataSource,
) : UserRepository {

    override suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User> {
        delay(FAKE_LATENCY_MS)
        val current = local.cachedUser.first() ?: return AppResult.Failure(AppError.Unauthorized)
        if (!NameValidator.isValid(name)) return AppResult.Failure(AppError.Validation(field = "name"))
        if (roles.isEmpty()) return AppResult.Failure(AppError.Validation(field = "roles"))

        val updated = current.copy(
            name = name.trim(),
            roles = roles,
            workerStatus = if (UserRole.Worker in roles) WorkerVerificationStatus.NotSubmitted else null,
        )
        local.saveUser(updated)
        return AppResult.Success(updated)
    }
}
