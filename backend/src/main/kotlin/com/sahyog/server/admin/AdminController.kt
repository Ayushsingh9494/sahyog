package com.sahyog.server.admin

import com.sahyog.server.api.ApiException
import com.sahyog.server.api.NotFoundException
import com.sahyog.server.api.ValidationException
import com.sahyog.server.security.toCaller
import com.sahyog.server.user.Access
import com.sahyog.server.user.UserRepository
import com.sahyog.server.user.WorkerStatus
import com.sahyog.server.worker.ServiceDto
import com.sahyog.server.worker.WorkerRepository
import org.springframework.http.HttpStatus
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.time.Clock
import java.time.LocalDate

/** The cooperative at a glance. */
data class AdminOverview(
    val pendingVerifications: Int,
    val verifiedWorkers: Int,
    val rejectedWorkers: Int,
    val customers: Int,
    val bookingsToday: Int,
    val liveJobs: Int,
    val completedThisWeek: Int,
    val averageRating: Double?,
    val recentDecisions: List<StatusChange>,
)

/** One entry of the audit trail. */
data class StatusChange(
    val workerUid: String,
    val workerName: String,
    val fromStatus: String?,
    val toStatus: String,
    /** Null when the worker caused it (e.g. submitting an ID). */
    val changedByName: String?,
    val reason: String?,
    val changedAtMillis: Long,
)

data class AdminWorkerSummary(
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val status: String,
    val locality: String?,
    val categoryIds: List<String>,
    val rating: Double,
    val ratingCount: Int,
    val jobsCompleted: Int,
    val isAvailable: Boolean,
    val joinedAtMillis: Long,
)

data class AdminWorkerDetail(
    val summary: AdminWorkerSummary,
    val about: String?,
    val yearsExperience: Int?,
    val languages: List<String>,
    val services: List<ServiceDto>,
    val documentType: String?,
    val documentSubmittedAtMillis: Long?,
    val history: List<StatusChange>,
)

data class RevokeRequest(val reason: String)

@Service
class AdminService(
    private val access: Access,
    private val jdbc: JdbcClient,
    private val workers: WorkerRepository,
    private val users: UserRepository,
    private val audit: StatusAudit,
    private val clock: Clock,
) {
    fun overview(adminUid: String): AdminOverview {
        access.admin(adminUid)
        val today = LocalDate.now(clock)
        val weekAgo = clock.instant().minusSeconds(7 * 24 * 3600)
        fun count(sql: String, vararg params: Pair<String, Any>): Int =
            jdbc.sql(sql).params(params.toMap()).query(Long::class.java).single().toInt()
        return AdminOverview(
            pendingVerifications = count("SELECT count(*) FROM users WHERE is_worker AND worker_status = 'Pending'"),
            verifiedWorkers = count("SELECT count(*) FROM users WHERE is_worker AND worker_status = 'Verified'"),
            rejectedWorkers = count("SELECT count(*) FROM users WHERE is_worker AND worker_status = 'Rejected'"),
            customers = count("SELECT count(*) FROM users WHERE is_customer"),
            bookingsToday = count("SELECT count(*) FROM bookings WHERE slot_date = :today", "today" to today),
            liveJobs = count("SELECT count(*) FROM bookings WHERE status IN ('Accepted', 'WorkerEnRoute', 'InProgress')"),
            completedThisWeek = count(
                "SELECT count(*) FROM bookings WHERE status = 'Completed' AND completed_at >= :since",
                "since" to weekAgo.atOffset(java.time.ZoneOffset.UTC),
            ),
            // Null until anyone has been rated.
            averageRating = jdbc.sql("SELECT avg(review_rating) FROM bookings WHERE review_rating IS NOT NULL")
                .query { rs, _ -> rs.getDouble(1).takeUnless { rs.wasNull() } }.list().firstOrNull()
                ?.let { Math.round(it * 10) / 10.0 },
            recentDecisions = changes("WHERE c.changed_by IS NOT NULL", emptyMap(), limit = 10),
        )
    }

    /** Workers, newest first; [status] filters, [query] matches name, phone or area. */
    fun workers(adminUid: String, status: String?, query: String?): List<AdminWorkerSummary> {
        access.admin(adminUid)
        if (status != null && WorkerStatus.entries.none { it.name == status }) throw ValidationException("status")
        val q = query?.trim()?.takeIf { it.isNotEmpty() }?.let { "%" + it.replace("%", "\\%") + "%" }
        return summaries(
            """
            WHERE u.is_worker
              AND (CAST(:status AS text) IS NULL OR u.worker_status = :status)
              AND (CAST(:q AS text) IS NULL OR u.name ILIKE :q OR u.phone_number LIKE :q OR p.locality ILIKE :q)
            """.trimIndent(),
            mapOf("status" to status, "q" to q),
        )
    }

    fun worker(adminUid: String, workerUid: String): AdminWorkerDetail {
        access.admin(adminUid)
        val summary = summaries("WHERE u.uid = :uid AND u.is_worker", mapOf("uid" to workerUid)).firstOrNull()
            ?: throw NotFoundException()
        val profile = workers.find(workerUid)
        val document = workers.latestDocument(workerUid)
        return AdminWorkerDetail(
            summary = summary,
            about = profile?.about,
            yearsExperience = profile?.yearsExperience,
            languages = profile?.languages.orEmpty(),
            services = profile?.services.orEmpty(),
            documentType = document?.type?.name,
            documentSubmittedAtMillis = document?.submittedAt?.toEpochMilli(),
            history = changes("WHERE c.worker_uid = :uid", mapOf("uid" to workerUid), limit = 50),
        )
    }

    /** Takes a verified worker out of the catalog (e.g. after complaints). They can re-apply. */
    @Transactional
    fun revoke(adminUid: String, workerUid: String, reason: String) {
        access.admin(adminUid)
        if (adminUid == workerUid) throw ApiException(HttpStatus.FORBIDDEN, "forbidden")
        val trimmed = reason.trim()
        if (trimmed.length !in 3..300) throw ValidationException("reason")
        val worker = users.find(workerUid) ?: throw NotFoundException()
        if (worker.workerStatus != WorkerStatus.Verified) throw ApiException(HttpStatus.CONFLICT, "conflict", "status")
        users.updateWorkerStatus(workerUid, WorkerStatus.Rejected)
        audit.record(workerUid, WorkerStatus.Verified, WorkerStatus.Rejected, adminUid, trimmed)
    }

    private fun summaries(where: String, params: Map<String, Any?>): List<AdminWorkerSummary> {
        val rows = jdbc.sql(
            """
            SELECT u.uid, u.name, u.phone_number, u.worker_status, u.created_at,
                   p.locality, COALESCE(p.is_available, false) AS is_available,
                   COALESCE(s.rating, 0) AS rating, COALESCE(s.rating_count, 0) AS rating_count,
                   COALESCE(s.jobs_completed, 0) AS jobs_completed
            FROM users u
            LEFT JOIN worker_profiles p ON p.uid = u.uid
            LEFT JOIN LATERAL (
                SELECT avg(b.review_rating) AS rating, count(b.review_rating) AS rating_count,
                       count(*) FILTER (WHERE b.status = 'Completed') AS jobs_completed
                FROM bookings b WHERE b.worker_uid = u.uid
            ) s ON true
            $where
            ORDER BY u.created_at DESC
            LIMIT 200
            """.trimIndent(),
        ).params(params).query { rs, _ -> rs.toSummary() }.list()
        if (rows.isEmpty()) return rows
        val categories = jdbc.sql(
            """
            SELECT wc.uid, wc.category_id FROM worker_categories wc JOIN categories c ON c.id = wc.category_id
            WHERE wc.uid IN (:uids) ORDER BY c.position
            """.trimIndent(),
        ).param("uids", rows.map { it.uid }).query { rs, _ -> rs.getString(1) to rs.getString(2) }.list()
            .groupBy({ it.first }, { it.second })
        return rows.map { it.copy(categoryIds = categories[it.uid].orEmpty()) }
    }

    private fun ResultSet.toSummary() = AdminWorkerSummary(
        uid = getString("uid"),
        name = getString("name"),
        phoneNumber = getString("phone_number"),
        status = getString("worker_status") ?: WorkerStatus.NotSubmitted.name,
        locality = getString("locality"),
        categoryIds = emptyList(),
        rating = Math.round(getDouble("rating") * 10) / 10.0,
        ratingCount = getInt("rating_count"),
        jobsCompleted = getInt("jobs_completed"),
        isAvailable = getBoolean("is_available"),
        joinedAtMillis = getTimestamp("created_at").time,
    )

    private fun changes(where: String, params: Map<String, Any?>, limit: Int): List<StatusChange> = jdbc.sql(
        """
        SELECT c.*, w.name AS worker_name, a.name AS admin_name
        FROM worker_status_changes c
        JOIN users w ON w.uid = c.worker_uid
        LEFT JOIN users a ON a.uid = c.changed_by
        $where
        ORDER BY c.changed_at DESC, c.id DESC
        LIMIT $limit
        """.trimIndent(),
    ).params(params).query { rs, _ ->
        StatusChange(
            workerUid = rs.getString("worker_uid"),
            workerName = rs.getString("worker_name"),
            fromStatus = rs.getString("from_status"),
            toStatus = rs.getString("to_status"),
            changedByName = rs.getString("admin_name"),
            reason = rs.getString("reason"),
            changedAtMillis = rs.getTimestamp("changed_at").time,
        )
    }.list()
}

/** Cooperative staff's console: overview, workers directory, worker detail and revocation. */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController(private val service: AdminService) {

    @GetMapping("/overview")
    fun overview(@AuthenticationPrincipal jwt: Jwt) = service.overview(jwt.toCaller().uid)

    @GetMapping("/workers")
    fun workers(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) q: String?,
    ) = service.workers(jwt.toCaller().uid, status, q)

    @GetMapping("/workers/{uid}")
    fun worker(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String) = service.worker(jwt.toCaller().uid, uid)

    @PostMapping("/workers/{uid}/revoke")
    fun revoke(@AuthenticationPrincipal jwt: Jwt, @PathVariable uid: String, @RequestBody request: RevokeRequest) =
        service.revoke(jwt.toCaller().uid, uid, request.reason)
}
