package com.sahyog.server.admin

import com.sahyog.server.api.ApiException
import com.sahyog.server.api.NotFoundException
import com.sahyog.server.api.ValidationException
import com.sahyog.server.security.toCaller
import com.sahyog.server.storage.DocumentStorage
import com.sahyog.server.user.Access
import com.sahyog.server.user.UserRepository
import com.sahyog.server.user.WorkerStatus
import com.sahyog.server.worker.IdDocumentType
import com.sahyog.server.worker.WorkerRepository
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** A worker waiting for the cooperative's decision. */
data class PendingVerification(
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val documentType: IdDocumentType,
    val submittedAtMillis: Long,
    val locality: String?,
    val categoryIds: List<String>,
    /** True when this worker was rejected before and is trying again. */
    val isResubmission: Boolean,
)

data class RejectRequest(val reason: String)

@Service
class VerificationService(
    private val access: Access,
    private val users: UserRepository,
    private val workers: WorkerRepository,
    private val storage: DocumentStorage,
    private val jdbc: JdbcClient,
    private val audit: StatusAudit,
) {
    /** Oldest first, so nobody waits longest. */
    fun pending(adminUid: String): List<PendingVerification> {
        access.admin(adminUid)
        return jdbc.sql(
            """
            SELECT u.uid, u.name, u.phone_number, d.document_type, d.submitted_at, p.locality,
                   ARRAY(SELECT wc.category_id FROM worker_categories wc JOIN categories c ON c.id = wc.category_id
                         WHERE wc.uid = u.uid ORDER BY c.position) AS category_ids,
                   EXISTS (SELECT 1 FROM worker_status_changes x
                           WHERE x.worker_uid = u.uid AND x.to_status = 'Rejected') AS resubmission
            FROM users u
            LEFT JOIN worker_profiles p ON p.uid = u.uid
            JOIN LATERAL (
                SELECT * FROM id_documents d WHERE d.uid = u.uid ORDER BY submitted_at DESC, id DESC LIMIT 1
            ) d ON true
            WHERE u.worker_status = 'Pending'
            ORDER BY d.submitted_at
            """.trimIndent(),
        ).query { rs, _ ->
            PendingVerification(
                uid = rs.getString("uid"),
                name = rs.getString("name"),
                phoneNumber = rs.getString("phone_number"),
                documentType = IdDocumentType.valueOf(rs.getString("document_type")),
                submittedAtMillis = rs.getTimestamp("submitted_at").toInstant().toEpochMilli(),
                locality = rs.getString("locality"),
                categoryIds = (rs.getArray("category_ids").array as Array<*>).map { it.toString() },
                isResubmission = rs.getBoolean("resubmission"),
            )
        }.list()
    }

    /** The latest document's image, for an admin to look at. */
    fun document(adminUid: String, workerUid: String): Pair<ByteArray, String> {
        access.admin(adminUid)
        val document = workers.latestDocument(workerUid) ?: throw NotFoundException()
        val bytes = storage.load(document.storageKey) ?: throw NotFoundException()
        return bytes to document.contentType
    }

    @Transactional
    fun approve(adminUid: String, workerUid: String) = decide(adminUid, workerUid, approve = true, reason = null)

    @Transactional
    fun reject(adminUid: String, workerUid: String, reason: String) {
        val trimmed = reason.trim()
        if (trimmed.length !in 3..300) throw ValidationException("reason")
        decide(adminUid, workerUid, approve = false, reason = trimmed)
    }

    private fun decide(adminUid: String, workerUid: String, approve: Boolean, reason: String?) {
        access.admin(adminUid)
        // Nobody verifies themselves, even an admin who is also a worker.
        if (adminUid == workerUid) throw ApiException(HttpStatus.FORBIDDEN, "forbidden")
        val worker = users.find(workerUid) ?: throw NotFoundException()
        val document = workers.latestDocument(workerUid) ?: throw NotFoundException()
        if (worker.workerStatus != WorkerStatus.Pending || document.decision != null) {
            throw ApiException(HttpStatus.CONFLICT, "conflict", "status")
        }
        workers.decide(document.id, if (approve) "Approved" else "Rejected", adminUid, reason)
        val to = if (approve) WorkerStatus.Verified else WorkerStatus.Rejected
        users.updateWorkerStatus(workerUid, to)
        audit.record(workerUid, worker.workerStatus, to, adminUid, reason)
    }
}

/**
 * Cooperative staff review workers' identity documents. Admin access is granted in the
 * database (`users.is_admin`), never through the API.
 */
@RestController
@RequestMapping("/api/v1/admin/verifications")
class VerificationController(private val service: VerificationService) {

    @GetMapping
    fun pending(@AuthenticationPrincipal jwt: Jwt) = service.pending(jwt.toCaller().uid)

    @GetMapping("/{uid}/document")
    fun document(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String): ResponseEntity<ByteArray> {
        val (bytes, contentType) = service.document(jwt.toCaller().uid, uid)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            // Identity documents must never sit in a browser or proxy cache.
            .cacheControl(CacheControl.noStore())
            .body(bytes)
    }

    @PostMapping("/{uid}/approve")
    fun approve(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String) =
        service.approve(jwt.toCaller().uid, uid)

    @PostMapping("/{uid}/reject")
    fun reject(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String, @RequestBody request: RejectRequest) =
        service.reject(jwt.toCaller().uid, uid, request.reason)
}
