package com.sahyog.core.data.repository

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.validation.NameValidator
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.datastore.StoredProfile
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Combines the verified identity (Firebase) with the profile (local stand-in for the
 * backend). Replaced by an API-backed implementation in Milestone 10; the interface,
 * and therefore every screen, stays the same.
 */
internal class LocalProfileUserRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val profiles: LocalProfileDataSource,
) : UserRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<User?> = authRepository.session.flatMapLatest { session ->
        if (session == null) flowOf(null) else profiles.profile(session.uid).map { session.toUser(it) }
    }

    override suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User> {
        val session = authRepository.session.first() ?: return AppResult.Failure(AppError.Unauthorized)
        if (!NameValidator.isValid(name)) return AppResult.Failure(AppError.Validation(field = "name"))
        if (roles.isEmpty()) return AppResult.Failure(AppError.Validation(field = "roles"))

        val profile = StoredProfile(
            name = name.trim(),
            roles = roles,
            workerStatus = if (UserRole.Worker in roles) WorkerVerificationStatus.NotSubmitted else null,
        )
        profiles.save(session.uid, profile)
        return AppResult.Success(session.toUser(profile))
    }
}

/** A session without a stored profile is a new user who still needs onboarding. */
private fun AuthSession.toUser(profile: StoredProfile?) = User(
    id = uid,
    phoneNumber = phoneNumber,
    name = profile?.name,
    roles = profile?.roles.orEmpty(),
    workerStatus = profile?.workerStatus,
)
