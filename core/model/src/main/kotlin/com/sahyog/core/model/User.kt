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
