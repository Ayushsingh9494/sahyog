package com.sahyog.core.data.booking

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.data.demo.DemoTestEnvironment
import com.sahyog.core.data.network.ApiException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class DemoBookingDataSourceTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Fictional catalog worker "Ramesh Kumar" (w-ramesh) offers "Fan installation" at ₹199. */
    private val request = NetworkBookingRequest(
        workerId = "w-ramesh",
        serviceName = "Fan installation",
        slotDate = "2026-11-23",
        slotStartHour = 11,
        address = "  12 Main Road, Model Town ",
        note = "  ",
    )

    private suspend fun TestScope.env() = DemoTestEnvironment(tmp, this).apply { signIn("u1", "Asha Devi") }

    @Test
    fun `creates a requested booking with the server price, trimmed input and the customer name`() = runTest {
        val env = env()
        val booking = env.bookings.createBooking(request)
        assertEquals("Requested", booking.status)
        assertEquals(19_900, booking.pricePaise)
        assertEquals("12 Main Road, Model Town", booking.address)
        assertEquals("Asha Devi", booking.customerName)
        assertNull(booking.note)
        assertEquals(listOf(booking), env.bookings.fetchBookings())
    }

    @Test
    fun `sample worker accepts after the delay and a start code is issued`() = runTest {
        val env = env()
        env.bookings.createBooking(request)
        env.now = env.now.plusSeconds(DemoServer.AUTO_ACCEPT_SECONDS - 1)
        assertEquals("Requested", env.bookings.fetchBookings().single().status)

        env.now = env.now.plusSeconds(1)
        val accepted = env.bookings.fetchBookings().single()
        assertEquals("Accepted", accepted.status)
        assertEquals(4, accepted.startCode?.length)
    }

    @Test
    fun `unaccepted request expires once its slot starts`() = runTest {
        val env = env()
        env.bookings.createBooking(request.copy(slotStartHour = 9))
        // Nobody read it (so nothing accepted it) before the visit was due.
        env.now = LocalDateTime.of(2026, 11, 23, 9, 0)
        assertEquals("Expired", env.bookings.fetchBookings().single().status)
    }

    @Test
    fun `rejects unknown worker, unknown service, past slot and short address`() = runTest {
        val source = env().bookings
        assertValidation(BookingErrorFields.WORKER) { source.createBooking(request.copy(workerId = "nobody")) }
        assertValidation(BookingErrorFields.SERVICE) { source.createBooking(request.copy(serviceName = "Painting")) }
        assertValidation(BookingErrorFields.SLOT) { source.createBooking(request.copy(slotStartHour = 7)) }
        assertValidation(BookingErrorFields.SLOT) { source.createBooking(request.copy(slotDate = "tomorrow")) }
        assertValidation(BookingErrorFields.ADDRESS) { source.createBooking(request.copy(address = "Flat 2")) }
    }

    @Test
    fun `a worker cannot be double booked, even by another customer`() = runTest {
        val env = env()
        env.bookings.createBooking(request)
        env.signIn("u2")
        assertValidation(BookingErrorFields.SLOT_TAKEN) { env.bookings.createBooking(request) }
    }

    @Test
    fun `bookings are private to each user`() = runTest {
        val env = env()
        env.bookings.createBooking(request)
        env.signIn("u2")
        assertEquals(emptyList<NetworkBooking>(), env.bookings.fetchBookings())
    }

    @Test
    fun `cancel is allowed while requested, then refused, and frees the slot`() = runTest {
        val source = env().bookings
        val booking = source.createBooking(request)
        assertEquals("Cancelled", source.cancelBooking(booking.id).status)
        assertValidation(BookingErrorFields.STATUS) { source.cancelBooking(booking.id) }
        assertNotNull(source.createBooking(request))
    }

    @Test
    fun `cancelling someone else's or an unknown booking is not found`() = runTest {
        val env = env()
        val booking = env.bookings.createBooking(request)
        env.signIn("u2")
        assertEquals(AppError.NotFound, errorOf { env.bookings.cancelBooking(booking.id) })
        assertEquals(AppError.NotFound, errorOf { env.bookings.cancelBooking("SH-NOPE") })
    }

    @Test
    fun `signed out requests are unauthorized`() = runTest {
        val env = env()
        env.signOut()
        assertEquals(AppError.Unauthorized, errorOf { env.bookings.fetchBookings() })
    }

    private suspend fun errorOf(block: suspend () -> Unit): AppError =
        (runCatching { block() }.exceptionOrNull() as ApiException).error

    private suspend fun assertValidation(field: String, block: suspend () -> Unit) {
        assertEquals(AppError.Validation(field), errorOf(block))
    }
}
