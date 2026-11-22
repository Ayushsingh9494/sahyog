package com.sahyog.core.data.repository

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.AuthSession
import com.sahyog.core.model.Booking
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Job
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserPreferences
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerProfile
import com.sahyog.core.model.WorkerProfileInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

/**
 * The service catalog customers browse: categories and bookable workers.
 * Reads come from the on-device cache and keep working offline; [refresh] updates it.
 */
interface CatalogRepository {
    /** Categories in the cooperative's display order. Empty until the first successful refresh. */
    val categories: Flow<List<ServiceCategory>>

    /** Workers, best rated first; all of them when [categoryId] is null. */
    fun workers(categoryId: String? = null): Flow<List<Worker>>

    /** Workers whose name, area, category (incl. Hindi keywords) or service matches [query]. */
    fun searchWorkers(query: String): Flow<List<Worker>>

    /** Emits null if the worker isn't (or is no longer) in the catalog. */
    fun worker(id: String): Flow<Worker?>

    /** Downloads the latest catalog. On failure the cached copy is left untouched. */
    suspend fun refresh(): AppResult<Unit>
}

/**
 * The signed-in customer's bookings. Reads are served from the device cache; creating
 * and cancelling require the server's confirmation.
 */
interface BookingRepository {
    /** Soonest visit first. */
    val bookings: Flow<List<Booking>>

    /** Emits null if the booking isn't in the cache. */
    fun booking(id: String): Flow<Booking?>

    /** Downloads the latest state of all bookings (e.g. a worker accepted). */
    suspend fun refresh(): AppResult<Unit>

    /**
     * Fails with `AppError.Validation` (field from `BookingErrorFields`) if the server
     * rejects the request, e.g. the slot was just taken.
     */
    suspend fun createBooking(request: BookingRequest): AppResult<Booking>

    /** Allowed only while the booking is Requested or Accepted. */
    suspend fun cancelBooking(bookingId: String): AppResult<Booking>

    /** Address from the most recent booking, to pre-fill the next one. */
    suspend fun lastUsedAddress(): String?
}

sealed interface WorkerProfileState {
    /** Not fetched yet in this session. */
    data object NotLoaded : WorkerProfileState

    /** The user holds the Worker role but never filled in a worker profile. */
    data object NotSetUp : WorkerProfileState

    data class Ready(val profile: WorkerProfile) : WorkerProfileState
}

/** The signed-in worker's own profile and verification. */
interface WorkerProfileRepository {
    val profile: StateFlow<WorkerProfileState>

    /** Fetches the profile; also how the app learns that verification finished. */
    suspend fun refresh(): AppResult<Unit>

    /** Creates or updates the profile. Fails with `AppError.Validation` if the server rejects it. */
    suspend fun saveProfile(input: WorkerProfileInput): AppResult<WorkerProfile>

    /**
     * Uploads a photo of an ID (a Photo Picker content URI) and starts verification.
     * Requires a saved profile.
     */
    suspend fun submitIdDocument(type: IdDocumentType, imageUri: String): AppResult<WorkerProfile>

    /** "Accepting jobs" switch, shown to customers as availability. */
    suspend fun setAvailability(available: Boolean): AppResult<WorkerProfile>
}

/**
 * Jobs assigned to the signed-in worker. Each action is one step of the job lifecycle;
 * the server refuses steps the current status doesn't allow (`AppError.Validation`
 * with `BookingErrorFields.STATUS`), e.g. accepting a job the customer just cancelled.
 */
interface JobRepository {
    /** Soonest visit first. */
    val jobs: Flow<List<Job>>

    fun job(id: String): Flow<Job?>

    suspend fun refresh(): AppResult<Unit>

    suspend fun accept(jobId: String): AppResult<Job>

    suspend fun decline(jobId: String): AppResult<Job>

    suspend fun startTrip(jobId: String): AppResult<Job>

    /** Fails with `WorkerErrorFields.START_CODE` if the code doesn't match. */
    suspend fun startJob(jobId: String, startCode: String): AppResult<Job>

    suspend fun complete(jobId: String): AppResult<Job>
}
