package com.sahyog.core.data.admin

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.network.NetworkAdminWorker
import com.sahyog.core.data.network.NetworkReason
import com.sahyog.core.data.network.NetworkStatusChange
import com.sahyog.core.data.network.SahyogApi
import com.sahyog.core.data.network.apiCall
import com.sahyog.core.data.repository.AdminRepository
import com.sahyog.core.model.AdminOverview
import com.sahyog.core.model.AdminWorker
import com.sahyog.core.model.AdminWorkerDetail
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.StatusChange
import com.sahyog.core.model.VerificationRequest
import com.sahyog.core.model.WorkerService
import com.sahyog.core.model.WorkerVerificationStatus
import java.time.Instant
import javax.inject.Inject

/** Live calls only: nothing from the admin console is stored on the phone. */
internal class ApiAdminRepository @Inject constructor(private val api: SahyogApi) : AdminRepository {

    override suspend fun overview(): AppResult<AdminOverview> = apiCall {
        val o = api.adminOverview()
        AdminOverview(
            pendingVerifications = o.pendingVerifications,
            verifiedWorkers = o.verifiedWorkers,
            rejectedWorkers = o.rejectedWorkers,
            customers = o.customers,
            bookingsToday = o.bookingsToday,
            liveJobs = o.liveJobs,
            completedThisWeek = o.completedThisWeek,
            averageRating = o.averageRating?.toFloat(),
            recentDecisions = o.recentDecisions.map { it.asExternalModel() },
        )
    }

    override suspend fun verificationQueue(): AppResult<List<VerificationRequest>> = apiCall {
        api.verifications().map {
            VerificationRequest(
                workerId = it.uid,
                name = it.name,
                phoneNumber = it.phoneNumber,
                documentType = IdDocumentType.entries.firstOrNull { type -> type.name == it.documentType }
                    ?: IdDocumentType.Aadhaar,
                submittedAt = Instant.ofEpochMilli(it.submittedAtMillis),
                locality = it.locality,
                categoryIds = it.categoryIds,
                isResubmission = it.isResubmission,
            )
        }
    }

    override suspend fun document(workerId: String): AppResult<ByteArray> = apiCall {
        api.document(workerId).use { it.bytes() }
    }

    override suspend fun approve(workerId: String): AppResult<Unit> = apiCall { api.approve(workerId) }

    override suspend fun reject(workerId: String, reason: String): AppResult<Unit> =
        apiCall { api.reject(workerId, NetworkReason(reason.trim())) }

    override suspend fun workers(status: WorkerVerificationStatus?, query: String?): AppResult<List<AdminWorker>> =
        apiCall { api.adminWorkers(status?.name, query?.trim()?.ifBlank { null }).map { it.asExternalModel() } }

    override suspend fun worker(workerId: String): AppResult<AdminWorkerDetail> = apiCall {
        val d = api.adminWorker(workerId)
        AdminWorkerDetail(
            worker = d.summary.asExternalModel(),
            about = d.about?.ifBlank { null },
            yearsExperience = d.yearsExperience,
            languages = d.languages,
            services = d.services.map { s ->
                WorkerService(
                    name = s.name,
                    startingPrice = Paise(s.pricePaise),
                    unit = PriceUnit.entries.firstOrNull { it.name == s.unit } ?: PriceUnit.PerVisit,
                )
            },
            documentType = IdDocumentType.entries.firstOrNull { it.name == d.documentType },
            documentSubmittedAt = d.documentSubmittedAtMillis?.let(Instant::ofEpochMilli),
            history = d.history.map { it.asExternalModel() },
        )
    }

    override suspend fun revoke(workerId: String, reason: String): AppResult<Unit> =
        apiCall { api.revoke(workerId, NetworkReason(reason.trim())) }
}

private fun String?.toStatus(): WorkerVerificationStatus? = WorkerVerificationStatus.entries.firstOrNull { it.name == this }

private fun NetworkStatusChange.asExternalModel() = StatusChange(
    workerId = workerUid,
    workerName = workerName,
    from = fromStatus.toStatus(),
    to = toStatus.toStatus() ?: WorkerVerificationStatus.NotSubmitted,
    changedBy = changedByName,
    reason = reason,
    changedAt = Instant.ofEpochMilli(changedAtMillis),
)

private fun NetworkAdminWorker.asExternalModel() = AdminWorker(
    id = uid,
    name = name,
    phoneNumber = phoneNumber,
    status = status.toStatus() ?: WorkerVerificationStatus.NotSubmitted,
    locality = locality,
    categoryIds = categoryIds,
    rating = rating.toFloat(),
    ratingCount = ratingCount,
    jobsCompleted = jobsCompleted,
    isAvailable = isAvailable,
    joinedAt = Instant.ofEpochMilli(joinedAtMillis),
)
