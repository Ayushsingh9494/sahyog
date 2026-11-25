package com.sahyog.core.model

import java.time.Duration
import java.time.Instant

/**
 * The customer's rating of one completed booking. A booking has at most one review,
 * and it can't be changed once submitted (so ratings can't be used to bargain).
 *
 * @property rating 1 to 5 stars.
 * @property comment Optional; already trimmed by the server.
 */
data class BookingReview(
    val rating: Int,
    val comment: String?,
    val createdAt: Instant,
)

/**
 * A review as shown publicly on a worker's profile.
 *
 * @property authorName The reviewer's first name only: their full name and phone stay private.
 */
data class WorkerReview(
    val id: String,
    val workerId: String,
    val authorName: String,
    val rating: Int,
    val comment: String?,
    val serviceName: String,
    val createdAt: Instant,
)

/** What the customer submits. The server checks the same rules. */
data class ReviewInput(val rating: Int, val comment: String?) {
    val isValid: Boolean
        get() = rating in MIN_RATING..MAX_RATING && (comment?.trim()?.length ?: 0) <= COMMENT_MAX_LENGTH

    companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5
        const val COMMENT_MAX_LENGTH = 500

        /** Reviews are accepted for this long after the job is completed. */
        val REVIEW_WINDOW: Duration = Duration.ofDays(14)
    }
}

/** Whether the customer can still rate this booking at [now]. */
fun Booking.canReview(now: Instant): Boolean {
    val doneAt = completedAt ?: return false
    return status == BookingStatus.Completed && review == null && now.isBefore(doneAt + ReviewInput.REVIEW_WINDOW)
}

/** "Asha Sharma" -> "Asha": how reviewers are named in public. */
fun publicFirstName(fullName: String?): String? =
    fullName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
