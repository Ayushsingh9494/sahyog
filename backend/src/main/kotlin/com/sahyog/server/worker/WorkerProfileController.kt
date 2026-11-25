package com.sahyog.server.worker

import com.sahyog.server.api.ValidationException
import com.sahyog.server.security.toCaller
import com.sahyog.server.storage.DocumentStorage
import com.sahyog.server.storage.ImageSniffer
import com.sahyog.server.user.Access
import com.sahyog.server.user.UserRepository
import com.sahyog.server.user.WorkerStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** The signed-in worker's own profile (the app's `NetworkWorkerProfile`). */
data class WorkerProfileResponse(
    val categoryIds: List<String>,
    val services: List<ServiceDto>,
    val about: String,
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
    val isAvailable: Boolean,
    val status: String,
    val documentType: IdDocumentType?,
    val documentSubmittedAtMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
)

data class AvailabilityRequest(val available: Boolean)

@Service
class WorkerProfileService(
    private val access: Access,
    private val workers: WorkerRepository,
    private val users: UserRepository,
    private val storage: DocumentStorage,
) {
    fun profile(uid: String): WorkerProfileResponse? {
        access.worker(uid)
        return workers.find(uid)?.let { toResponse(it) }
    }

    /** Editing details keeps the verification decision: it's about the person, not the prices. */
    @Transactional
    fun save(uid: String, input: WorkerProfileInput): WorkerProfileResponse {
        access.worker(uid)
        if (!WorkerProfileRules.isValid(input, workers.categoryIds())) throw ValidationException(WorkerErrorFields.PROFILE)
        workers.save(uid, input, input.location()?.coarsened())
        return toResponse(workers.find(uid)!!)
    }

    fun setAvailability(uid: String, available: Boolean): WorkerProfileResponse {
        access.worker(uid)
        if (workers.find(uid) == null) throw ValidationException(WorkerErrorFields.NO_PROFILE)
        workers.setAvailability(uid, available)
        return toResponse(workers.find(uid)!!)
    }

    /**
     * Stores the photo privately and puts the worker in the review queue. A verified worker
     * re-submitting (e.g. a renewed licence) stays verified while the new one is checked.
     */
    @Transactional
    fun submitDocument(uid: String, type: String, image: ByteArray): WorkerProfileResponse {
        val user = access.worker(uid)
        if (workers.find(uid) == null) throw ValidationException(WorkerErrorFields.NO_PROFILE)
        val documentType = IdDocumentType.entries.find { it.name == type }
            ?: throw ValidationException(WorkerErrorFields.DOCUMENT)
        if (image.size > WorkerProfileRules.MAX_DOCUMENT_BYTES) throw ValidationException(WorkerErrorFields.DOCUMENT_TOO_LARGE)
        // Judge the file by its content, not by the name or type the client claims.
        val contentType = ImageSniffer.contentType(image) ?: throw ValidationException(WorkerErrorFields.DOCUMENT)

        val key = storage.save(image)
        workers.addDocument(uid, documentType, key, contentType)
        if (user.workerStatus != WorkerStatus.Verified) users.updateWorkerStatus(uid, WorkerStatus.Pending)
        return toResponse(workers.find(uid)!!)
    }

    private fun toResponse(record: WorkerRecord): WorkerProfileResponse {
        val document = workers.latestDocument(record.uid)
        return WorkerProfileResponse(
            categoryIds = record.categoryIds,
            services = record.services,
            about = record.about,
            locality = record.locality,
            languages = record.languages,
            yearsExperience = record.yearsExperience,
            isAvailable = record.isAvailable,
            status = record.status,
            documentType = document?.type,
            documentSubmittedAtMillis = document?.submittedAt?.toEpochMilli(),
            latitude = record.location?.latitude,
            longitude = record.location?.longitude,
        )
    }
}

@RestController
@RequestMapping("/api/v1/worker/profile")
class WorkerProfileController(private val service: WorkerProfileService) {

    /** 204 (no body) when the worker hasn't filled in the profile form yet. */
    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<WorkerProfileResponse> =
        service.profile(jwt.toCaller().uid)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PutMapping
    fun save(@AuthenticationPrincipal jwt: Jwt, @RequestBody input: WorkerProfileInput) =
        service.save(jwt.toCaller().uid, input)

    @PutMapping("/availability")
    fun availability(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: AvailabilityRequest) =
        service.setAvailability(jwt.toCaller().uid, request.available)

    /** Multipart: `type` (an IdDocumentType name) and `image` (the photo). */
    @PostMapping("/document")
    fun document(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam type: String,
        @RequestParam image: MultipartFile,
    ) = service.submitDocument(jwt.toCaller().uid, type, image.bytes)
}
