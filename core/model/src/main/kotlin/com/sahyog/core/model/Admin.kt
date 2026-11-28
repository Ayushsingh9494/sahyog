package com.sahyog.core.model

import java.time.Instant

/** The cooperative at a glance, for its staff. */
data class AdminOverview(
    val pendingVerifications: Int,
    val verifiedWorkers: Int,
    val rejectedWorkers: Int,
    val customers: Int,
    val bookingsToday: Int,
    val liveJobs: Int,
    val completedThisWeek: Int,
    /** Across all reviews; null until anyone has been rated. */
    val averageRating: Float?,
    val recentDecisions: List<StatusChange>,
    /** Money paid through the app so far, by how it was paid. */
    val collectedOnline: Paise = Paise(0),
    val collectedCash: Paise = Paise(0),
    /** Completed jobs not paid yet. */
    val awaitingPayment: Int = 0,
)

/**
 * One change of a worker's verification status: the audit trail.
 * @property changedBy The admin's name; null when the worker caused it (e.g. submitting an ID).
 */
data class StatusChange(
    val workerId: String,
    val workerName: String,
    val from: WorkerVerificationStatus?,
    val to: WorkerVerificationStatus,
    val changedBy: String?,
    val reason: String?,
    val changedAt: Instant,
)

/** A worker waiting for the cooperative to check their ID. */
data class VerificationRequest(
    val workerId: String,
    val name: String,
    val phoneNumber: String,
    val documentType: IdDocumentType,
    val submittedAt: Instant,
    val locality: String?,
    val categoryIds: List<String>,
    /** Rejected before and trying again. */
    val isResubmission: Boolean,
)

/** A worker as listed in the admin directory. */
data class AdminWorker(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val status: WorkerVerificationStatus,
    val locality: String?,
    val categoryIds: List<String>,
    val rating: Float,
    val ratingCount: Int,
    val jobsCompleted: Int,
    val isAvailable: Boolean,
    val joinedAt: Instant,
)

data class AdminWorkerDetail(
    val worker: AdminWorker,
    val about: String?,
    val yearsExperience: Int?,
    val languages: List<String>,
    val services: List<WorkerService>,
    val documentType: IdDocumentType?,
    val documentSubmittedAt: Instant?,
    /** Newest first. */
    val history: List<StatusChange>,
)

/** Common reasons offered when rejecting or revoking, so decisions are quick and consistent. */
enum class AdminReason { PhotoUnclear, NameMismatch, DocumentNotAccepted, DocumentExpired, Complaints, Other }
