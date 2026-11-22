package com.sahyog.core.data.worker

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.datastore.LocalProfileDataSource
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.WorkerProfileInput
import com.sahyog.core.model.WorkerVerificationStatus
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

    private fun NetworkWorkerProfileInput.isValid(): Boolean = WorkerProfileInput.isValid(
        categoryCount = categoryIds.distinct().size,
        services = services.map { it.name to it.pricePaise },
        about = about,
        locality = locality,
        languageCount = languages.distinct().size,
        yearsExperience = yearsExperience,
    )
}
