package com.sahyog.core.data.review

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.booking.NetworkBookingRequest
import com.sahyog.core.data.booking.NetworkReviewRequest
import com.sahyog.core.data.catalog.DemoCatalogDataSource
import com.sahyog.core.data.catalog.NetworkWorkerService
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.data.demo.DemoTestEnvironment
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.data.worker.NetworkWorkerProfileInput
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.model.UserRole
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoReviewDataSourceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val ravi = setOf(UserRole.Customer, UserRole.Worker)

    private suspend fun TestScope.env() = DemoTestEnvironment(tmp, this)

    /** Ravi (a real, verified worker) completes Asha's booking; ends signed in as Asha. */
    private suspend fun DemoTestEnvironment.completedBooking(slotHour: Int = 11): String {
        signIn("ravi", "Ravi Kumar", ravi)
        workers.saveProfile(
            NetworkWorkerProfileInput(
                categoryIds = listOf("electrician"),
                services = listOf(NetworkWorkerService("Fan installation", 25_000, "PerVisit")),
                about = "Ten years of house wiring.",
                locality = "Model Town",
                languages = listOf("Hindi"),
                yearsExperience = 10,
            ),
        )
        workers.submitIdDocument("Aadhaar", ByteArray(1_000) { 1 }, "image/jpeg")
        now = now.plusSeconds(DemoServer.AUTO_VERIFY_SECONDS)
        workers.fetchMyProfile()

        signIn("asha", "Asha Devi")
        val id = bookings.createBooking(
            NetworkBookingRequest("ravi", "Fan installation", "2026-11-23", slotHour, "12 Main Road, Model Town"),
        ).id

        signIn("ravi", "Ravi Kumar", ravi)
        val code = workers.acceptJob(id).startCode!!
        workers.startTrip(id)
        workers.startJob(id, code)
        workers.completeJob(id)
        signIn("asha", "Asha Devi")
        return id
    }

    @Test
    fun `customer rates a completed booking once, and it shows publicly by first name`() = runTest {
        val env = env()
        val id = env.completedBooking()

        val reviewed = env.bookings.reviewBooking(id, NetworkReviewRequest(5, "  Fixed it quickly. "))
        assertEquals(5, reviewed.review?.rating)
        assertEquals("Fixed it quickly.", reviewed.review?.comment)
        assertEquals(
            AppError.Validation(BookingErrorFields.REVIEW_CLOSED),
            errorOf { env.bookings.reviewBooking(id, NetworkReviewRequest(1)) },
        )

        val public = env.reviews.fetchWorkerReviews("ravi").single()
        assertEquals("Asha", public.authorName)
        assertEquals("Fan installation", public.serviceName)

        val listed = env.catalog.fetchCatalog().workers.single { it.id == "ravi" }
        assertEquals(5f, listed.rating)
        assertEquals(1, listed.ratingCount)
    }

    @Test
    fun `the worker sees the rating on the job`() = runTest {
        val env = env()
        val id = env.completedBooking()
        env.bookings.reviewBooking(id, NetworkReviewRequest(4))
        env.signIn("ravi", "Ravi Kumar", ravi)
        assertEquals(4, env.workers.fetchJobs().single().review?.rating)
    }

    @Test
    fun `invalid ratings and comments are refused`() = runTest {
        val env = env()
        val id = env.completedBooking()
        for (bad in listOf(NetworkReviewRequest(0), NetworkReviewRequest(6), NetworkReviewRequest(5, "x".repeat(501)))) {
            assertEquals(AppError.Validation(BookingErrorFields.REVIEW), errorOf { env.bookings.reviewBooking(id, bad) })
        }
    }

    @Test
    fun `only completed bookings, only by their customer, only within the window`() = runTest {
        val env = env()
        val id = env.completedBooking()

        // Someone else can't even see it.
        env.signIn("other", "Other Person")
        assertEquals(AppError.NotFound, errorOf { env.bookings.reviewBooking(id, NetworkReviewRequest(1)) })

        // A booking that isn't completed yet.
        env.signIn("asha", "Asha Devi")
        val open = env.bookings.createBooking(
            NetworkBookingRequest("ravi", "Fan installation", "2026-11-24", 15, "12 Main Road, Model Town"),
        )
        assertEquals(
            AppError.Validation(BookingErrorFields.REVIEW_CLOSED),
            errorOf { env.bookings.reviewBooking(open.id, NetworkReviewRequest(5)) },
        )

        // Too late.
        env.now = env.now.plus(ReviewInput.REVIEW_WINDOW)
        assertEquals(
            AppError.Validation(BookingErrorFields.REVIEW_CLOSED),
            errorOf { env.bookings.reviewBooking(id, NetworkReviewRequest(5)) },
        )
    }

    @Test
    fun `sample workers have sample reviews and new ratings fold into their average`() = runTest {
        val env = env()
        val samples = env.reviews.fetchWorkerReviews("w-ramesh")
        assertEquals(3, samples.size)
        // Varied, not the same text three times.
        assertEquals(3, samples.map { it.comment }.distinct().size)
        assertEquals(3, samples.map { it.authorName }.distinct().size)
        assertTrue(samples.zipWithNext().all { (a, b) -> a.createdAtMillis >= b.createdAtMillis })
        assertTrue(env.reviews.fetchWorkerReviews("nobody").isEmpty())

        assertEquals(4.8f to 213, DemoCatalogDataSource.combinedRating(4.8f, 212, listOf(5)))
        assertEquals(3.0f to 2, DemoCatalogDataSource.combinedRating(0f, 0, listOf(5, 1)))
        assertEquals(0f to 0, DemoCatalogDataSource.combinedRating(0f, 0, emptyList()))
    }

    private suspend fun errorOf(block: suspend () -> Unit): AppError =
        (runCatching { block() }.exceptionOrNull() as ApiException).error
}
