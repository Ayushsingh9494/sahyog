package com.sahyog.core.data.booking

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.catalog.CatalogNetworkDataSource
import com.sahyog.core.data.catalog.NetworkCatalog
import com.sahyog.core.data.catalog.NetworkCategory
import com.sahyog.core.data.catalog.NetworkWorker
import com.sahyog.core.data.catalog.NetworkWorkerService
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.model.AuthSession
import com.sahyog.core.testing.TestAuthRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class DemoBookingDataSourceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val testZone: ZoneId = ZoneOffset.UTC
    private var now: LocalDateTime = LocalDateTime.of(2026, 11, 23, 8, 0)
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = testZone
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now.toInstant(ZoneOffset.UTC)
    }

    private val auth = TestAuthRepository().apply { setSession(AuthSession("u1", "9876543210")) }

    private val catalog = object : CatalogNetworkDataSource {
        override suspend fun fetchCatalog() = NetworkCatalog(
            categories = listOf(NetworkCategory("electrician", "Electrician", emptyList())),
            workers = listOf(
                NetworkWorker(
                    id = "w1", name = "Ramesh Kumar", categoryIds = listOf("electrician"),
                    rating = 4.8f, ratingCount = 1, jobsCompleted = 1, yearsExperience = 1,
                    locality = "Model Town", about = "", languages = emptyList(),
                    services = listOf(NetworkWorkerService("Fan installation", 19_900, "PerVisit")),
                    isAvailable = true,
                ),
            ),
        )
    }

    private val request = NetworkBookingRequest(
        workerId = "w1",
        serviceName = "Fan installation",
        slotDate = "2026-11-23",
        slotStartHour = 11,
        address = "  12 Main Road, Model Town ",
        note = "  ",
    )

    private fun TestScope.source() = DemoBookingDataSource(
        storeFile = tmp.root.resolve("bookings.json"),
        authRepository = auth,
        catalog = catalog,
        clock = clock,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        random = Random(42),
        latencyMs = 0,
    )

    @Test
    fun `creates a requested booking with the server price, trimmed input and no blank note`() = runTest {
        val booking = source().createBooking(request)
        assertEquals("Requested", booking.status)
        assertEquals(19_900, booking.pricePaise)
        assertEquals("12 Main Road, Model Town", booking.address)
        assertNull(booking.note)
        assertEquals(listOf(booking), source().fetchBookings())
    }

    @Test
    fun `worker accepts after the delay and a start code is issued`() = runTest {
        val source = source()
        source.createBooking(request)
        now = now.plusSeconds(DemoBookingDataSource.AUTO_ACCEPT_SECONDS - 1)
        assertEquals("Requested", source.fetchBookings().single().status)

        now = now.plusSeconds(1)
        val accepted = source.fetchBookings().single()
        assertEquals("Accepted", accepted.status)
        assertEquals(4, accepted.startCode?.length)
    }

    @Test
    fun `unaccepted request expires once its slot starts`() = runTest {
        val source = source()
        source.createBooking(request.copy(slotStartHour = 9))
        // Nobody read it (so nothing accepted it) before the visit was due.
        now = LocalDateTime.of(2026, 11, 23, 9, 0)
        assertEquals("Expired", source.fetchBookings().single().status)
    }

    @Test
    fun `rejects unknown worker, unknown service, past slot and short address`() = runTest {
        val source = source()
        assertValidation(BookingErrorFields.WORKER) { source.createBooking(request.copy(workerId = "nobody")) }
        assertValidation(BookingErrorFields.SERVICE) { source.createBooking(request.copy(serviceName = "Painting")) }
        assertValidation(BookingErrorFields.SLOT) { source.createBooking(request.copy(slotStartHour = 7)) }
        assertValidation(BookingErrorFields.SLOT) { source.createBooking(request.copy(slotDate = "tomorrow")) }
        assertValidation(BookingErrorFields.ADDRESS) { source.createBooking(request.copy(address = "Flat 2")) }
    }

    @Test
    fun `a worker cannot be double booked, even by another customer`() = runTest {
        source().createBooking(request)
        auth.setSession(AuthSession("u2", "9123456789"))
        assertValidation(BookingErrorFields.SLOT_TAKEN) { source().createBooking(request) }
    }

    @Test
    fun `bookings are private to each user`() = runTest {
        source().createBooking(request)
        auth.setSession(AuthSession("u2", "9123456789"))
        assertEquals(emptyList<NetworkBooking>(), source().fetchBookings())
    }

    @Test
    fun `cancel is allowed while requested, then refused`() = runTest {
        val source = source()
        val booking = source.createBooking(request)
        assertEquals("Cancelled", source.cancelBooking(booking.id).status)
        assertValidation(BookingErrorFields.STATUS) { source.cancelBooking(booking.id) }
        // Cancelling frees the slot for others.
        assertNotNull(source.createBooking(request))
    }

    @Test
    fun `cancelling an unknown booking is not found`() = runTest {
        val error = runCatching { source().cancelBooking("SH-NOPE") }.exceptionOrNull() as ApiException
        assertEquals(AppError.NotFound, error.error)
    }

    @Test
    fun `signed out requests are unauthorized`() = runTest {
        auth.setSession(null)
        val error = runCatching { source().fetchBookings() }.exceptionOrNull() as ApiException
        assertEquals(AppError.Unauthorized, error.error)
    }

    private suspend fun assertValidation(field: String, block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertEquals(AppError.Validation(field), (error as ApiException).error)
    }
}
