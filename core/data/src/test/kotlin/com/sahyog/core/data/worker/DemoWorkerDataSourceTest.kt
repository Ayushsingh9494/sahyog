package com.sahyog.core.data.worker

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.booking.NetworkBookingRequest
import com.sahyog.core.data.catalog.NetworkWorkerService
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.data.demo.DemoTestEnvironment
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class DemoWorkerDataSourceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val input = NetworkWorkerProfileInput(
        categoryIds = listOf("electrician"),
        services = listOf(NetworkWorkerService(" Fan installation ", 25_000, "PerVisit")),
        about = "Ten years of house wiring.",
        locality = "Model Town",
        languages = listOf("Hindi", "Punjabi"),
        yearsExperience = 10,
    )
    private val photo = ByteArray(1_000) { 1 }

    private suspend fun TestScope.env() = DemoTestEnvironment(tmp, this).apply {
        signIn("ravi", "Ravi Kumar", setOf(UserRole.Customer, UserRole.Worker))
    }

    @Test
    fun `no profile until saved, then saved as not submitted`() = runTest {
        val env = env()
        assertNull(env.workers.fetchMyProfile())
        val saved = env.workers.saveProfile(input)
        assertEquals("Fan installation", saved.services.single().name)
        assertEquals(WorkerVerificationStatus.NotSubmitted.name, saved.status)
        assertEquals(saved, env.workers.fetchMyProfile())
    }

    @Test
    fun `invalid profile is rejected`() = runTest {
        val env = env()
        assertEquals(
            AppError.Validation(WorkerErrorFields.PROFILE),
            errorOf { env.workers.saveProfile(input.copy(categoryIds = emptyList())) },
        )
        assertEquals(
            AppError.Validation(WorkerErrorFields.PROFILE),
            errorOf { env.workers.saveProfile(input.copy(services = listOf(NetworkWorkerService("Fan", 1_000, "PerVisit")))) },
        )
    }

    @Test
    fun `document starts review and the cooperative verifies after the delay`() = runTest {
        val env = env()
        env.workers.saveProfile(input)
        val pending = env.workers.submitIdDocument(IdDocumentType.Aadhaar.name, photo, "image/jpeg")
        assertEquals(WorkerVerificationStatus.Pending.name, pending.status)
        assertEquals(WorkerVerificationStatus.Pending, env.profiles.profile("ravi").first()?.workerStatus)

        env.now = env.now.plusSeconds(DemoServer.AUTO_VERIFY_SECONDS)
        assertEquals(WorkerVerificationStatus.Verified.name, env.workers.fetchMyProfile()?.status)
        // The user record (what the app's session reads) follows the decision.
        assertEquals(WorkerVerificationStatus.Verified, env.profiles.profile("ravi").first()?.workerStatus)
    }

    @Test
    fun `document needs a saved profile, an image type and a sane size`() = runTest {
        val env = env()
        assertEquals(
            AppError.Validation(WorkerErrorFields.NO_PROFILE),
            errorOf { env.workers.submitIdDocument("Aadhaar", photo, "image/jpeg") },
        )
        env.workers.saveProfile(input)
        assertEquals(
            AppError.Validation(WorkerErrorFields.DOCUMENT),
            errorOf { env.workers.submitIdDocument("Aadhaar", photo, "application/pdf") },
        )
        assertEquals(
            AppError.Validation(WorkerErrorFields.DOCUMENT),
            errorOf { env.workers.submitIdDocument("Passport", photo, "image/jpeg") },
        )
        assertEquals(
            AppError.Validation(WorkerErrorFields.DOCUMENT_TOO_LARGE),
            errorOf { env.workers.submitIdDocument("Aadhaar", ByteArray(IdDocumentType.MAX_IMAGE_BYTES + 1), "image/png") },
        )
    }

    @Test
    fun `editing the profile keeps the verification`() = runTest {
        val env = env()
        env.workers.saveProfile(input)
        env.workers.submitIdDocument("Aadhaar", photo, "image/jpeg")
        env.now = env.now.plusSeconds(DemoServer.AUTO_VERIFY_SECONDS)
        val edited = env.workers.saveProfile(input.copy(about = "Now also inverters."))
        assertEquals(WorkerVerificationStatus.Verified.name, edited.status)
    }

    @Test
    fun `only verified workers are listed in the catalog, and they can be booked`() = runTest {
        val env = env()
        env.workers.saveProfile(input)
        assertTrue(env.catalog.fetchCatalog().workers.none { it.id == "ravi" })

        env.workers.submitIdDocument("Aadhaar", photo, "image/jpeg")
        env.now = env.now.plusSeconds(DemoServer.AUTO_VERIFY_SECONDS)
        val listed = env.catalog.fetchCatalog().workers.single { it.id == "ravi" }
        assertEquals("Ravi Kumar", listed.name)
        assertEquals(0, listed.ratingCount)

        val request = NetworkBookingRequest("ravi", "Fan installation", "2026-11-24", 11, "12 Main Road, Model Town")
        // A worker can't book themselves...
        assertEquals(
            AppError.Validation(BookingErrorFields.WORKER),
            errorOf { env.bookings.createBooking(request) },
        )
        // ...but a customer can, and a real worker's request is NOT auto-accepted.
        env.signIn("asha", "Asha Devi")
        env.bookings.createBooking(request)
        env.now = env.now.plusSeconds(DemoServer.AUTO_ACCEPT_SECONDS * 10)
        assertEquals("Requested", env.bookings.fetchBookings().single().status)
    }

    /** Ravi verified and listed; Asha books him; ends signed in as Ravi. Returns the job id. */
    private suspend fun DemoTestEnvironment.bookedJob(): String {
        workers.saveProfile(input)
        workers.submitIdDocument("Aadhaar", photo, "image/jpeg")
        now = now.plusSeconds(DemoServer.AUTO_VERIFY_SECONDS)
        workers.fetchMyProfile()
        signIn("asha", "Asha Devi")
        val booking = bookings.createBooking(
            NetworkBookingRequest("ravi", "Fan installation", "2026-11-24", 11, "12 Main Road, Model Town"),
        )
        signIn("ravi", "Ravi Kumar", setOf(UserRole.Customer, UserRole.Worker))
        return booking.id
    }

    @Test
    fun `full job lifecycle, visible to the customer at every step`() = runTest {
        val env = env()
        val jobId = env.bookedJob()

        val request = env.workers.fetchJobs().single()
        assertEquals("Asha Devi", request.customerName)
        assertEquals("Requested", request.status)

        val code = env.workers.acceptJob(jobId).startCode!!
        assertEquals(AppError.Validation(BookingErrorFields.STATUS), errorOf { env.workers.startJob(jobId, code) })
        // The visit is tomorrow at 11: setting off a day early is refused, 3 hours before is fine.
        assertEquals(AppError.Validation(WorkerErrorFields.TOO_EARLY), errorOf { env.workers.startTrip(jobId) })
        env.now = LocalDateTime.of(2026, 11, 24, 8, 0)
        env.workers.startTrip(jobId)
        assertEquals(
            AppError.Validation(WorkerErrorFields.START_CODE),
            errorOf { env.workers.startJob(jobId, if (code == "1111") "2222" else "1111") },
        )
        assertEquals("InProgress", env.workers.startJob(jobId, " $code ").status)
        env.now = env.now.plusHours(1)
        val done = env.workers.completeJob(jobId)
        assertEquals("Completed", done.status)
        assertEquals(env.clock.millis(), done.completedAtMillis)

        env.signIn("asha", "Asha Devi")
        assertEquals("Completed", env.bookings.fetchBookings().single().status)
    }

    @Test
    fun `customer cannot cancel once the worker is on the way`() = runTest {
        val env = env()
        val jobId = env.bookedJob()
        env.workers.acceptJob(jobId)
        env.now = LocalDateTime.of(2026, 11, 24, 10, 0)
        env.workers.startTrip(jobId)
        env.signIn("asha", "Asha Devi")
        assertEquals(AppError.Validation(BookingErrorFields.STATUS), errorOf { env.bookings.cancelBooking(jobId) })
    }

    @Test
    fun `declined or cancelled jobs can't be accepted`() = runTest {
        val env = env()
        val jobId = env.bookedJob()
        assertEquals("Rejected", env.workers.declineJob(jobId).status)
        assertEquals(AppError.Validation(BookingErrorFields.STATUS), errorOf { env.workers.acceptJob(jobId) })
    }

    @Test
    fun `workers only see and act on their own jobs`() = runTest {
        val env = env()
        val jobId = env.bookedJob()
        env.signIn("other", "Other Worker", setOf(UserRole.Worker))
        assertTrue(env.workers.fetchJobs().isEmpty())
        assertEquals(AppError.NotFound, errorOf { env.workers.acceptJob(jobId) })
    }

    @Test
    fun `work location is stored at about 1 km precision and shown in the catalog`() = runTest {
        val env = env()
        env.workers.saveProfile(input.copy(latitude = 31.308512, longitude = 75.587134))
        assertEquals(31.31, env.workers.fetchMyProfile()?.latitude)
        assertEquals(75.59, env.workers.fetchMyProfile()?.longitude)
        assertEquals(
            AppError.Validation(WorkerErrorFields.PROFILE),
            errorOf { env.workers.saveProfile(input.copy(latitude = 95.0, longitude = 75.0)) },
        )

        env.workers.submitIdDocument("Aadhaar", photo, "image/jpeg")
        env.now = env.now.plusSeconds(DemoServer.AUTO_VERIFY_SECONDS)
        val catalog = env.catalog.fetchCatalog().workers
        assertEquals(31.31, catalog.single { it.id == "ravi" }.latitude)
        // Every sample worker has a point, so distances work out of the box.
        assertTrue(catalog.filter { it.id != "ravi" }.all { it.latitude != null && it.longitude != null })
    }

    @Test
    fun `availability switch is saved`() = runTest {
        val env = env()
        env.workers.saveProfile(input)
        assertEquals(false, env.workers.setAvailability(false).isAvailable)
        assertEquals(false, env.workers.fetchMyProfile()?.isAvailable)
    }

    private suspend fun errorOf(block: suspend () -> Unit): AppError =
        (runCatching { block() }.exceptionOrNull() as ApiException).error
}
