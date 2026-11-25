package com.sahyog.core.data.review

import com.sahyog.core.data.catalog.CatalogNetworkDataSource
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.model.publicFirstName
import javax.inject.Inject
import kotlin.math.absoluteValue

/**
 * TEMPORARY public reviews API on top of [DemoServer], until the Spring Boot API exists
 * (Milestone 10). Returns the reviews customers wrote in the app, plus a few clearly
 * fictional sample reviews for the fictional sample workers, so their profiles aren't
 * bare in the demo. Reviewers are shown by first name only.
 */
internal class DemoReviewDataSource @Inject constructor(
    private val server: DemoServer,
    private val catalog: CatalogNetworkDataSource,
) : ReviewNetworkDataSource {

    override suspend fun fetchWorkerReviews(workerId: String): List<NetworkWorkerReview> {
        // Looked up first, outside the server lock: the catalog is a request of its own.
        val worker = catalog.fetchCatalog().workers.find { it.id == workerId } ?: return emptyList()
        return server.publicCall {
            val written = state.bookings
                .filter { it.booking.workerId == workerId }
                .mapNotNull { stored ->
                    val booking = stored.booking
                    val review = booking.review ?: return@mapNotNull null
                    NetworkWorkerReview(
                        id = "r-${booking.id}",
                        workerId = workerId,
                        authorName = publicFirstName(booking.customerName) ?: ANONYMOUS,
                        rating = review.rating,
                        comment = review.comment,
                        serviceName = booking.serviceName,
                        createdAtMillis = review.createdAtMillis,
                    )
                }
            val samples = if (workerId.startsWith(SAMPLE_ID_PREFIX)) {
                sampleReviews(workerId, worker.services.map { it.name }, nowMillis)
            } else {
                emptyList()
            }
            (written + samples)
                .sortedByDescending { it.createdAtMillis }
                .take(ReviewNetworkDataSource.RECENT_REVIEWS_LIMIT)
        }
    }

    private fun sampleReviews(workerId: String, services: List<String>, nowMillis: Long): List<NetworkWorkerReview> {
        val seed = workerId.hashCode().absoluteValue
        return SAMPLE_DAYS_AGO.mapIndexed { index, daysAgo ->
            // seed + index walks through each list, so one worker's samples never repeat.
            val pick = seed + index
            NetworkWorkerReview(
                id = "sample-$workerId-$index",
                workerId = workerId,
                authorName = SAMPLE_AUTHORS[pick % SAMPLE_AUTHORS.size],
                rating = if (pick % 3 == 0) 4 else 5,
                comment = SAMPLE_COMMENTS[pick % SAMPLE_COMMENTS.size],
                serviceName = services.getOrElse(index % services.size.coerceAtLeast(1)) { "" },
                createdAtMillis = nowMillis - daysAgo * DAY_MILLIS,
            )
        }
    }

    private companion object {
        const val SAMPLE_ID_PREFIX = "w-"
        const val ANONYMOUS = "Customer"
        const val DAY_MILLIS = 24L * 60 * 60 * 1_000
        val SAMPLE_DAYS_AGO = listOf(3L, 11L, 26L)

        // Fictional, for the fictional sample workers only.
        val SAMPLE_AUTHORS = listOf("Neha", "Amit", "Priya", "Rohit", "Simran", "Karan", "Anjali", "Vikram")
        val SAMPLE_COMMENTS = listOf(
            "Came on time and explained the problem clearly before starting.",
            "Neat work and cleaned up afterwards.",
            "Fair price, no extra charges. Will book again.",
            "Polite and quick. Fixed it in one visit.",
            "Brought the right tools and spare parts.",
            null,
            "Very professional. Showed me how to avoid the issue in future.",
        )
    }
}
