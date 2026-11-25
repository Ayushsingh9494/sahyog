package com.sahyog.core.data.review

import kotlinx.serialization.Serializable

/**
 * Public reviews API (Sahyog server). Reviews are written through the bookings API, since
 * only the customer of a completed booking may write one.
 */
interface ReviewNetworkDataSource {
    /** The most recent reviews of [workerId], newest first, at most [RECENT_REVIEWS_LIMIT]. */
    suspend fun fetchWorkerReviews(workerId: String): List<NetworkWorkerReview>

    companion object {
        const val RECENT_REVIEWS_LIMIT = 20
    }
}

/** @property authorName First name only; the server never sends reviewers' full names or numbers. */
@Serializable
data class NetworkWorkerReview(
    val id: String,
    val workerId: String,
    val authorName: String,
    val rating: Int,
    val comment: String? = null,
    val serviceName: String,
    val createdAtMillis: Long,
)
