package com.sahyog.core.data.worker

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.booking.NetworkBooking
import com.sahyog.core.data.booking.status
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.JOB_START_WINDOW_HOURS
import com.sahyog.core.model.JobAction
import com.sahyog.core.model.allowedJobActions
import com.sahyog.core.model.WorkerProfileInput
import com.sahyog.core.model.WorkerVerificationStatus
import java.time.LocalDate
import javax.inject.Inject

/**
 * TEMPORARY worker API on top of [DemoServer], until the Spring Boot API exists (M10).
 *
 * ID images are validated (an image, within the size limit) and then **discarded**:
 * the demo never keeps identity documents. Real storage (encrypted, server-side,
 * access-controlled) comes with the backend.
 */
internal class DemoWorkerDataSource @Inject constructor(
    private val server: DemoServer,
    private val profiles: LocalProfileDataSource,
) : WorkerNetworkDataSource {

    override suspend fun fetchMyProfile(): NetworkWorkerProfile? = server.call { uid -> state.workers[uid] }

    override suspend fun saveProfile(input: NetworkWorkerProfileInput): NetworkWorkerProfile = server.call { uid ->
        if (!input.isValid()) throw ApiException(AppError.Validation(WorkerErrorFields.PROFILE))
        val existing = state.workers[uid]
        val saved = NetworkWorkerProfile(
            categoryIds = input.categoryIds.distinct(),
            services = input.services.map { it.copy(name = it.name.trim()) },
            about = input.about.trim(),
            locality = input.locality.trim(),
            languages = input.languages.distinct(),
            yearsExperience = input.yearsExperience,
            isAvailable = existing?.isAvailable ?: true,
            // Editing details keeps the verification decision; it's about the person, not the prices.
            status = existing?.status ?: WorkerVerificationStatus.NotSubmitted.name,
            documentType = existing?.documentType,
            documentSubmittedAtMillis = existing?.documentSubmittedAtMillis,
        )
        state = state.copy(workers = state.workers + (uid to saved))
        saved
    }

    override suspend fun submitIdDocument(type: String, image: ByteArray, mimeType: String): NetworkWorkerProfile =
        server.call { uid ->
            val profile = state.workers[uid] ?: throw ApiException(AppError.Validation(WorkerErrorFields.NO_PROFILE))
            if (IdDocumentType.entries.none { it.name == type } || !mimeType.startsWith("image/") || image.isEmpty()) {
                throw ApiException(AppError.Validation(WorkerErrorFields.DOCUMENT))
            }
            if (image.size > IdDocumentType.MAX_IMAGE_BYTES) {
                throw ApiException(AppError.Validation(WorkerErrorFields.DOCUMENT_TOO_LARGE))
            }
            // A verified worker re-submitting stays verified; everyone else goes to review.
            val status = if (profile.status == WorkerVerificationStatus.Verified.name) {
                WorkerVerificationStatus.Verified
            } else {
                WorkerVerificationStatus.Pending
            }
            val updated = profile.copy(
                status = status.name,
                documentType = type,
                documentSubmittedAtMillis = nowMillis,
            )
            profiles.updateWorkerStatus(uid, status)
            state = state.copy(workers = state.workers + (uid to updated))
            updated
        }

    override suspend fun setAvailability(available: Boolean): NetworkWorkerProfile = server.call { uid ->
        val profile = state.workers[uid] ?: throw ApiException(AppError.Validation(WorkerErrorFields.NO_PROFILE))
        val updated = profile.copy(isAvailable = available)
        state = state.copy(workers = state.workers + (uid to updated))
        updated
    }

    override suspend fun fetchJobs(): List<NetworkBooking> = server.call { uid ->
        state.bookings.filter { it.booking.workerId == uid }.map { it.booking }
    }

    override suspend fun acceptJob(jobId: String) = transition(jobId, JobAction.Accept) {
        copy(status = BookingStatus.Accepted.name, startCode = server.newStartCode())
    }

    override suspend fun declineJob(jobId: String) = transition(jobId, JobAction.Decline) {
        copy(status = BookingStatus.Rejected.name)
    }

    override suspend fun startTrip(jobId: String) = transition(jobId, JobAction.StartTrip) {
        copy(status = BookingStatus.WorkerEnRoute.name)
    }

    override suspend fun startJob(jobId: String, startCode: String) = transition(jobId, JobAction.StartJob) {
        if (startCode.trim() != this.startCode) throw ApiException(AppError.Validation(WorkerErrorFields.START_CODE))
        copy(status = BookingStatus.InProgress.name)
    }

    override suspend fun completeJob(jobId: String) = transition(jobId, JobAction.Complete) { nowMillis ->
        copy(status = BookingStatus.Completed.name, completedAtMillis = nowMillis)
    }

    /**
     * Applies one step of the job lifecycle to a booking assigned to the signed-in worker,
     * refusing it if the current status doesn't allow [action] (e.g. the customer cancelled).
     */
    private suspend fun transition(
        jobId: String,
        action: JobAction,
        change: NetworkBooking.(nowMillis: Long) -> NetworkBooking,
    ): NetworkBooking = server.call { uid ->
        val stored = state.bookings.find { it.booking.workerId == uid && it.booking.id == jobId }
            ?: throw ApiException(AppError.NotFound)
        if (action !in stored.booking.status().allowedJobActions()) {
            throw ApiException(AppError.Validation(BookingErrorFields.STATUS))
        }
        if (action == JobAction.StartTrip || action == JobAction.StartJob) {
            val visit = LocalDate.parse(stored.booking.slotDate).atTime(stored.booking.slotStartHour, 0)
            if (now.isBefore(visit.minusHours(JOB_START_WINDOW_HOURS))) {
                throw ApiException(AppError.Validation(WorkerErrorFields.TOO_EARLY))
            }
        }
        val updated = stored.booking.change(nowMillis)
        state = state.copy(bookings = state.bookings.map { if (it.booking.id == jobId) it.copy(booking = updated) else it })
        updated
    }

    private fun NetworkWorkerProfileInput.isValid(): Boolean = WorkerProfileInput.isValid(
        categoryCount = categoryIds.distinct().size,
        services = services.map { it.name to it.pricePaise },
        about = about,
        locality = locality,
        languageCount = languages.distinct().size,
        yearsExperience = yearsExperience,
    )
}
