package com.sahyog.core.data.booking

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.entity.BookingEntity
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.CheckoutResult
import com.sahyog.core.model.PaymentMethod
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class OfflineFirstBookingRepositoryTest {

    private val dao = FakeBookingDao()
    private val network = FakeNetwork()
    private val repository = OfflineFirstBookingRepository(dao, network)

    private val request = BookingRequest(
        workerId = "w1",
        serviceName = "Fan installation",
        slot = TimeSlot(LocalDate.of(2026, 11, 24), 11),
        address = " 12 Main Road, Model Town ",
        note = " ",
    )

    @Test
    fun `create sends a trimmed request and caches the confirmed booking`() = runTest {
        val result = repository.createBooking(request)

        assertEquals("12 Main Road, Model Town", network.lastRequest?.address)
        assertEquals(null, network.lastRequest?.note)
        assertEquals("2026-11-24", network.lastRequest?.slotDate)
        val booking = (result as AppResult.Success).data
        assertEquals(listOf(booking), repository.bookings.first())
        assertEquals(TimeSlot(LocalDate.of(2026, 11, 24), 11), booking.slot)
    }

    @Test
    fun `refused create caches nothing and surfaces the reason`() = runTest {
        network.failure = ApiException(AppError.Validation(BookingErrorFields.SLOT_TAKEN))
        assertEquals(
            AppResult.Failure(AppError.Validation(BookingErrorFields.SLOT_TAKEN)),
            repository.createBooking(request),
        )
        assertTrue(repository.bookings.first().isEmpty())
    }

    @Test
    fun `refresh replaces the cache with the server list`() = runTest {
        repository.createBooking(request)
        network.serverBookings = listOf(network.serverBookings.single().copy(status = "Accepted", startCode = "4821"))

        assertEquals(AppResult.Success(Unit), repository.refresh())
        val booking = repository.bookings.first().single()
        assertEquals(BookingStatus.Accepted, booking.status)
        assertEquals("4821", booking.startCode)
    }

    @Test
    fun `offline refresh keeps the cache`() = runTest {
        repository.createBooking(request)
        network.failure = IOException()
        assertEquals(AppResult.Failure(AppError.Network), repository.refresh())
        assertEquals(1, repository.bookings.first().size)
    }

    @Test
    fun `cancel updates the cached booking`() = runTest {
        val booking = (repository.createBooking(request) as AppResult.Success).data
        repository.cancelBooking(booking.id)
        assertEquals(BookingStatus.Cancelled, repository.booking(booking.id).first()?.status)
    }

    @Test
    fun `the chosen payment method travels with the booking`() = runTest {
        val booking = (repository.createBooking(request.copy(paymentMethod = PaymentMethod.Online)) as AppResult.Success).data
        assertEquals("Online", network.lastRequest?.paymentMethod)
        assertEquals(PaymentMethod.Online, booking.payment.method)
        assertEquals(false, booking.payment.isPaid)
    }

    @Test
    fun `a payment the server confirmed is cached as paid, a refused one changes nothing`() = runTest {
        val booking = (repository.createBooking(request.copy(paymentMethod = PaymentMethod.Online)) as AppResult.Success).data
        val order = (repository.startOnlinePayment(booking.id) as AppResult.Success).data
        assertEquals(19_900L, order.amount.value)

        network.failure = ApiException(AppError.Validation(BookingErrorFields.PAYMENT_FAILED))
        assertEquals(
            AppResult.Failure(AppError.Validation(BookingErrorFields.PAYMENT_FAILED)),
            repository.confirmOnlinePayment(booking.id, CheckoutResult.Completed(order.orderId, "pay_1", "sig")),
        )
        assertEquals(false, repository.booking(booking.id).first()?.payment?.isPaid)

        network.failure = null
        repository.confirmOnlinePayment(booking.id, CheckoutResult.Completed(order.orderId, "pay_1", "sig"))
        val payment = repository.booking(booking.id).first()?.payment
        assertEquals(true, payment?.isPaid)
        assertEquals(PaymentMethod.Online, payment?.via)
        assertEquals("pay_1", network.lastPaymentId)
    }

    @Test
    fun `a confirmed review is cached with the booking`() = runTest {
        val booking = (repository.createBooking(request) as AppResult.Success).data
        repository.submitReview(booking.id, ReviewInput(rating = 4, comment = "  Tidy work  "))
        val review = repository.booking(booking.id).first()?.review
        assertEquals(4, review?.rating)
        assertEquals("Tidy work", review?.comment)
    }

    @Test
    fun `sign-out cleanup empties the cache`() = runTest {
        repository.createBooking(request)
        repository.clearUserData()
        assertTrue(repository.bookings.first().isEmpty())
    }

    private class FakeNetwork : BookingNetworkDataSource {
        var failure: Exception? = null
        var lastRequest: NetworkBookingRequest? = null
        var serverBookings = emptyList<NetworkBooking>()

        override suspend fun fetchBookings(): List<NetworkBooking> {
            failure?.let { throw it }
            return serverBookings
        }

        override suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking {
            failure?.let { throw it }
            lastRequest = request
            val booking = NetworkBooking(
                id = "SH-ABC234", workerId = request.workerId, workerName = "Ramesh Kumar",
                serviceName = request.serviceName, pricePaise = 19_900, priceUnit = "PerVisit",
                slotDate = request.slotDate, slotStartHour = request.slotStartHour,
                address = request.address, note = request.note, status = "Requested", createdAtMillis = 1,
                paymentMethod = request.paymentMethod,
            )
            serverBookings = serverBookings + booking
            return booking
        }

        override suspend fun cancelBooking(bookingId: String): NetworkBooking {
            failure?.let { throw it }
            val cancelled = serverBookings.first { it.id == bookingId }.copy(status = "Cancelled")
            serverBookings = serverBookings.map { if (it.id == bookingId) cancelled else it }
            return cancelled
        }

        override suspend fun reviewBooking(bookingId: String, review: NetworkReviewRequest): NetworkBooking {
            failure?.let { throw it }
            val reviewed = serverBookings.first { it.id == bookingId }
                .copy(review = NetworkReview(review.rating, review.comment, createdAtMillis = 5))
            serverBookings = serverBookings.map { if (it.id == bookingId) reviewed else it }
            return reviewed
        }

        var lastPaymentId: String? = null

        override suspend fun paymentConfig() = NetworkPaymentConfig(onlineEnabled = true, keyId = "rzp_test_fake")

        override suspend fun createPaymentOrder(bookingId: String): NetworkPaymentOrder {
            failure?.let { throw it }
            val booking = serverBookings.first { it.id == bookingId }
            return NetworkPaymentOrder(bookingId, "order_1", booking.pricePaise, "INR", "rzp_test_fake", booking.serviceName)
        }

        override suspend fun verifyPayment(bookingId: String, result: NetworkPaymentResult): NetworkBooking {
            failure?.let { throw it }
            lastPaymentId = result.paymentId
            val paid = serverBookings.first { it.id == bookingId }
                .copy(paymentStatus = "Paid", paidVia = "Online", paidAtMillis = 9)
            serverBookings = serverBookings.map { if (it.id == bookingId) paid else it }
            return paid
        }
    }

    private class FakeBookingDao : BookingDao {
        private val rows = MutableStateFlow<Map<String, BookingEntity>>(emptyMap())

        override fun observeBookings(): Flow<List<BookingEntity>> = rows.map { it.values.toList() }
        override fun observeBooking(id: String): Flow<BookingEntity?> = rows.map { it[id] }
        override suspend fun lastUsedAddress(): String? = rows.value.values.maxByOrNull { it.createdAt }?.address
        override suspend fun upsert(booking: BookingEntity) {
            rows.value = rows.value + (booking.id to booking)
        }
        override suspend fun upsertAll(bookings: List<BookingEntity>) = bookings.forEach { upsert(it) }
        override suspend fun deleteAll() {
            rows.value = emptyMap()
        }
        override suspend fun replaceAll(bookings: List<BookingEntity>) {
            rows.value = bookings.associateBy { it.id }
        }
    }
}
