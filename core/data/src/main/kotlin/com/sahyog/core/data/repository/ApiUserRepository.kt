package com.sahyog.core.data.repository

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.common.validation.NameValidator
import com.sahyog.core.data.network.NetworkProfileRequest
import com.sahyog.core.data.network.NetworkUser
import com.sahyog.core.data.network.SahyogApi
import com.sahyog.core.data.network.apiCall
import com.sahyog.core.data.session.UserDataCleaner
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.datastore.StoredProfile
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.UserState
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** `GET/PUT /api/v1/me`, behind an interface so the repository can be tested without HTTP. */
internal interface UserNetworkDataSource {
    /** Null when the user is signed in but has never completed onboarding. */
    suspend fun fetchMe(): NetworkUser?

    suspend fun saveMe(name: String, roles: Set<UserRole>): NetworkUser
}

internal class RetrofitUserDataSource @Inject constructor(private val api: SahyogApi) : UserNetworkDataSource {
    override suspend fun fetchMe(): NetworkUser? {
        val response = api.me()
        return when {
            response.code() == HTTP_NOT_FOUND -> null
            response.isSuccessful -> response.body()
            else -> throw HttpException(response)
        }
    }

    override suspend fun saveMe(name: String, roles: Set<UserRole>): NetworkUser =
        api.saveMe(NetworkProfileRequest(name, roles.map { it.name }.toSet()))

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

/** Told when a worker's verification status changes (e.g. after the cooperative approves them). */
internal fun interface WorkerStatusListener {
    suspend fun onWorkerStatus(status: WorkerVerificationStatus)
}

/**
 * The profile from the Sahyog server, with an on-device copy so the app opens immediately
 * (and offline) after the first successful load. Each sign-in refreshes it once in the
 * background; the server's answer always wins (e.g. a profile deleted on the server sends
 * the user back to onboarding).
 */
@Singleton
internal class ApiUserRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val network: UserNetworkDataSource,
    private val cache: LocalProfileDataSource,
) : UserRepository, UserDataCleaner, WorkerStatusListener {

    private enum class Sync { Loading, Done, NotOnboarded, Failed }

    /** Per uid: how the latest fetch went. Missing = never fetched in this process. */
    private val sync = MutableStateFlow<Map<String, Sync>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    override val userState: Flow<UserState> = authRepository.session.flatMapLatest { session ->
        if (session == null) {
            flowOf<UserState>(UserState.SignedOut)
        } else {
            channelFlow {
                if (sync.value[session.uid] == null) launch { load(session) }
                combine(cache.profile(session.uid), sync.map { it[session.uid] }) { cached, state ->
                    when {
                        cached != null -> UserState.Ready(session.toUser(cached))
                        state == Sync.NotOnboarded -> UserState.Ready(session.toUser(null))
                        state == Sync.Failed -> UserState.Unavailable
                        else -> UserState.Loading
                    }
                }.collect { send(it) }
            }
        }
    }.distinctUntilChanged()

    override val currentUser: Flow<User?> = userState.map { (it as? UserState.Ready)?.user }.distinctUntilChanged()

    override suspend fun refresh(): AppResult<Unit> {
        val session = authRepository.session.first() ?: return AppResult.Failure(AppError.Unauthorized)
        return load(session)
    }

    override suspend fun completeOnboarding(name: String, roles: Set<UserRole>): AppResult<User> {
        val session = authRepository.session.first() ?: return AppResult.Failure(AppError.Unauthorized)
        if (!NameValidator.isValid(name)) return AppResult.Failure(AppError.Validation(field = "name"))
        if (roles.isEmpty()) return AppResult.Failure(AppError.Validation(field = "roles"))
        return apiCall {
            val profile = network.saveMe(name.trim(), roles).toStoredProfile()
            cache.save(session.uid, profile)
            setSync(session.uid, Sync.Done)
            session.toUser(profile)
        }
    }

    override suspend fun onWorkerStatus(status: WorkerVerificationStatus) {
        val session = authRepository.session.first() ?: return
        cache.updateWorkerStatus(session.uid, status)
    }

    override suspend fun clearUserData() {
        cache.clearAll()
        sync.value = emptyMap()
    }

    private suspend fun load(session: AuthSession): AppResult<Unit> {
        setSync(session.uid, Sync.Loading)
        val result = apiCall {
            val profile = network.fetchMe()
            if (profile == null) {
                cache.remove(session.uid)
                setSync(session.uid, Sync.NotOnboarded)
            } else {
                cache.save(session.uid, profile.toStoredProfile())
                setSync(session.uid, Sync.Done)
            }
        }
        // A cached profile keeps the app usable; without one the UI offers "Try again" and sign-out.
        if (result is AppResult.Failure) setSync(session.uid, Sync.Failed)
        return result
    }

    private fun setSync(uid: String, state: Sync) = sync.update { it + (uid to state) }
}

private fun NetworkUser.toStoredProfile() = StoredProfile(
    name = name,
    roles = roles.mapNotNull { role -> UserRole.entries.firstOrNull { it.name == role } }.toSet(),
    workerStatus = WorkerVerificationStatus.entries.firstOrNull { it.name == workerStatus },
)

/** A session without a profile is a new user who still needs onboarding. */
private fun AuthSession.toUser(profile: StoredProfile?) = User(
    id = uid,
    phoneNumber = phoneNumber,
    name = profile?.name,
    roles = profile?.roles.orEmpty(),
    workerStatus = profile?.workerStatus,
)
