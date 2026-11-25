package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.AdminRepository
import com.sahyog.core.model.AdminOverview
import com.sahyog.core.model.AdminWorker
import com.sahyog.core.model.AdminWorkerDetail
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.VerificationRequest
import com.sahyog.core.model.WorkerService
import com.sahyog.core.model.WorkerVerificationStatus
import java.time.Instant

/** In-memory admin console: set what each call returns, inspect the decisions made. */
class TestAdminRepository : AdminRepository {
    var overviewResult: AppResult<AdminOverview> = AppResult.Success(TestAdmin.overview())
    var queueResult: AppResult<List<VerificationRequest>> = AppResult.Success(emptyList())
    var documentResult: AppResult<ByteArray> = AppResult.Success(byteArrayOf(1, 2, 3))
    var decisionResult: AppResult<Unit> = AppResult.Success(Unit)
    val workers = mutableMapOf<String, AdminWorkerDetail>()

    val decisions = mutableListOf<Triple<String, String, String?>>()
    val workerQueries = mutableListOf<Pair<WorkerVerificationStatus?, String?>>()

    override suspend fun overview() = overviewResult

    override suspend fun verificationQueue() = queueResult

    override suspend fun document(workerId: String) = documentResult

    override suspend fun approve(workerId: String) = decide("approve", workerId, null)

    override suspend fun reject(workerId: String, reason: String) = decide("reject", workerId, reason)

    override suspend fun revoke(workerId: String, reason: String) = decide("revoke", workerId, reason)

    override suspend fun workers(status: WorkerVerificationStatus?, query: String?): AppResult<List<AdminWorker>> {
        workerQueries += status to query
        return AppResult.Success(
            workers.values.map { it.worker }
                .filter { status == null || it.status == status }
                .filter { query.isNullOrBlank() || it.name.contains(query, ignoreCase = true) },
        )
    }

    override suspend fun worker(workerId: String): AppResult<AdminWorkerDetail> =
        workers[workerId]?.let { AppResult.Success(it) } ?: AppResult.Failure(com.sahyog.core.common.result.AppError.NotFound)

    private fun decide(action: String, workerId: String, reason: String?): AppResult<Unit> {
        decisions += Triple(action, workerId, reason)
        return decisionResult
    }
}

object TestAdmin {
    val now: Instant = Instant.parse("2026-11-26T06:00:00Z")

    fun overview(pending: Int = 2) = AdminOverview(
        pendingVerifications = pending,
        verifiedWorkers = 18,
        rejectedWorkers = 1,
        customers = 40,
        bookingsToday = 5,
        liveJobs = 2,
        completedThisWeek = 12,
        averageRating = 4.6f,
        recentDecisions = emptyList(),
    )

    fun request(id: String, hoursAgo: Long = 2) = VerificationRequest(
        workerId = id,
        name = "Worker $id",
        phoneNumber = "+919123456789",
        documentType = IdDocumentType.Aadhaar,
        submittedAt = now.minusSeconds(hoursAgo * 3600),
        locality = "Model Town",
        categoryIds = listOf("electrician"),
        isResubmission = false,
    )

    fun worker(id: String, status: WorkerVerificationStatus, name: String = "Worker $id") = AdminWorkerDetail(
        worker = AdminWorker(
            id = id,
            name = name,
            phoneNumber = "+919123456789",
            status = status,
            locality = "Model Town",
            categoryIds = listOf("electrician"),
            rating = 0f,
            ratingCount = 0,
            jobsCompleted = 0,
            isAvailable = true,
            joinedAt = now,
        ),
        about = null,
        yearsExperience = 6,
        languages = listOf("Hindi"),
        services = listOf(WorkerService("Fan repair", Paise.rupees(300), PriceUnit.PerVisit)),
        documentType = if (status == WorkerVerificationStatus.NotSubmitted) null else IdDocumentType.Aadhaar,
        documentSubmittedAt = now,
        history = emptyList(),
    )
}
