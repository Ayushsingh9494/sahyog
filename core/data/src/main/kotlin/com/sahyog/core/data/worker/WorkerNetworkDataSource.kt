package com.sahyog.core.data.worker

import com.sahyog.core.data.catalog.NetworkWorkerService
import kotlinx.serialization.Serializable

/**
 * The worker-side API, acting for the signed-in user. Implemented by
 * [DemoWorkerDataSource] until the Spring Boot API exists (Milestone 10).
 *
 * Failures: `ApiException` for refused requests, `IOException` for connectivity.
 */
interface WorkerNetworkDataSource {
    /** Null if this user never set up a worker profile. */
    suspend fun fetchMyProfile(): NetworkWorkerProfile?

    /** Creates or updates the profile. Editing it does not reset verification. */
    suspend fun saveProfile(input: NetworkWorkerProfileInput): NetworkWorkerProfile

    /**
     * Uploads a photo of an identity document and starts verification (status Pending).
     * @param type An `IdDocumentType` name.
     */
    suspend fun submitIdDocument(type: String, image: ByteArray, mimeType: String): NetworkWorkerProfile

    suspend fun setAvailability(available: Boolean): NetworkWorkerProfile
}

/** Field names used in `AppError.Validation` for worker requests. */
object WorkerErrorFields {
    const val PROFILE = "profile"
    const val DOCUMENT = "document"
    const val DOCUMENT_TOO_LARGE = "document_too_large"
    const val NO_PROFILE = "no_profile"
}

/**
 * @property status A `WorkerVerificationStatus` name.
 * @property documentType An `IdDocumentType` name; the image itself is never sent back.
 */
@Serializable
data class NetworkWorkerProfile(
    val categoryIds: List<String>,
    val services: List<NetworkWorkerService>,
    val about: String,
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
    val isAvailable: Boolean = true,
    val status: String,
    val documentType: String? = null,
    val documentSubmittedAtMillis: Long? = null,
)

@Serializable
data class NetworkWorkerProfileInput(
    val categoryIds: List<String>,
    val services: List<NetworkWorkerService>,
    val about: String,
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
)
