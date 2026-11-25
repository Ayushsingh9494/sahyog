package com.sahyog.core.model

/** A role a person can hold on the platform. One account may hold both. */
enum class UserRole { Customer, Worker }

/** Cooperative verification of a worker's identity and skills. */
enum class WorkerVerificationStatus {
    /** Has the Worker role but hasn't submitted documents yet. */
    NotSubmitted,
    Pending,
    Verified,
    Rejected,
}

/**
 * The signed-in person as the backend knows them. Roles always come from the
 * server; the client never grants itself a role.
 *
 * @property name Null until onboarding is completed.
 * @property workerStatus Null when the user doesn't hold the Worker role.
 */
data class User(
    val id: String,
    val phoneNumber: String,
    val name: String?,
    val roles: Set<UserRole>,
    val workerStatus: WorkerVerificationStatus?,
) {
    val hasCompletedOnboarding: Boolean
        get() = !name.isNullOrBlank() && roles.isNotEmpty()
}

/** What the app knows about the signed-in person's profile right now. */
sealed interface UserState {
    data object SignedOut : UserState

    /** Signed in; the profile is being fetched for the first time on this device. */
    data object Loading : UserState

    /** Signed in, but the profile couldn't be fetched and none is cached (e.g. offline). */
    data object Unavailable : UserState

    /** The profile is known (possibly from the cache); [User.name] is null until onboarding. */
    data class Ready(val user: User) : UserState
}
