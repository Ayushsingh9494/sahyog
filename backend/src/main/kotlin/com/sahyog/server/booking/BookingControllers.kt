package com.sahyog.server.booking

import com.sahyog.server.security.toCaller
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The customer's bookings. */
@RestController
@RequestMapping("/api/v1/bookings")
class BookingController(private val service: BookingService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = service.customerBookings(jwt.toCaller().uid)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: BookingRequest) =
        service.create(jwt.toCaller().uid, request)

    @PostMapping("/{id}/cancel")
    fun cancel(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) = service.cancel(jwt.toCaller().uid, id)

    @PostMapping("/{id}/review")
    fun review(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String, @RequestBody request: ReviewRequest) =
        service.review(jwt.toCaller().uid, id, request)
}

/** The worker's jobs: the same bookings seen from the other side, one lifecycle step per call. */
@RestController
@RequestMapping("/api/v1/worker/jobs")
class JobController(private val service: BookingService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = service.workerJobs(jwt.toCaller().uid)

    @PostMapping("/{id}/accept")
    fun accept(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) =
        service.act(jwt.toCaller().uid, id, JobAction.Accept)

    @PostMapping("/{id}/decline")
    fun decline(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) =
        service.act(jwt.toCaller().uid, id, JobAction.Decline)

    @PostMapping("/{id}/start-trip")
    fun startTrip(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) =
        service.act(jwt.toCaller().uid, id, JobAction.StartTrip)

    /** Needs the code the customer reads out. */
    @PostMapping("/{id}/start")
    fun start(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String, @RequestBody request: StartJobRequest) =
        service.act(jwt.toCaller().uid, id, JobAction.StartJob, request.startCode)

    @PostMapping("/{id}/complete")
    fun complete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) =
        service.act(jwt.toCaller().uid, id, JobAction.Complete)
}

/** A review as shown on a worker's profile (the app's `NetworkWorkerReview`). */
data class PublicReview(
    val id: String,
    val workerId: String,
    /** First name only: reviewers' full names and numbers stay private. */
    val authorName: String,
    val rating: Int,
    val comment: String?,
    val serviceName: String,
    val createdAtMillis: Long,
)

@RestController
@RequestMapping("/api/v1/workers")
class ReviewController(private val jdbc: JdbcClient) {

    /** The most recent reviews, newest first. */
    @GetMapping("/{id}/reviews")
    fun reviews(@PathVariable id: String): List<PublicReview> = jdbc.sql(
        """
        SELECT id, worker_uid, customer_name, review_rating, review_comment, service_name, reviewed_at
        FROM bookings
        WHERE worker_uid = :id AND review_rating IS NOT NULL
        ORDER BY reviewed_at DESC
        LIMIT $RECENT_REVIEWS_LIMIT
        """.trimIndent(),
    ).param("id", id).query { rs, _ ->
        PublicReview(
            id = "r-" + rs.getString("id"),
            workerId = rs.getString("worker_uid"),
            authorName = firstName(rs.getString("customer_name")),
            rating = rs.getInt("review_rating"),
            comment = rs.getString("review_comment"),
            serviceName = rs.getString("service_name"),
            createdAtMillis = rs.getTimestamp("reviewed_at").toInstant().toEpochMilli(),
        )
    }.list()

    private fun firstName(full: String): String =
        full.trim().split(Regex("\\s+")).firstOrNull()?.ifBlank { null } ?: "Customer"

    private companion object {
        const val RECENT_REVIEWS_LIMIT = 20
    }
}
