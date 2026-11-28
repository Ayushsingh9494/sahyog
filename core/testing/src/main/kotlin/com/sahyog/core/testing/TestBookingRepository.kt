package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.BookingRepository
import com.sahyog.core.model.Booking
import com.sahyog.core.model.BookingPayment
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.CheckoutResult
import com.sahyog.core.model.PaymentMethod
import com.sahyog.core.model.PaymentOrder
import com.sahyog.core.model.BookingReview
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.BookingWorker
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/** In-memory bookings. Successful creates/cancels update the list like the real cache would. */
class TestBookingRepository : BookingRepository {
    private val bookingsFlow = MutableStateFlow<List<Booking>>(emptyList())

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)

    /** Null: succeed by turning the request into a Requested booking. */
    var createResult: AppResult<Booking>? = null
    var cancelResult: AppResult<Booking>? = null

    /** Null: succeed by attaching the review to the booking. */
    var reviewResult: AppResult<Booking>? = null
    val reviews = mutableListOf<Pair<String, ReviewInput>>()
    var lastAddress: String? = null

    var onlinePaymentEnabled = true

    /** Null: succeed with an order for the booking's price. */
    var orderResult: AppResult<PaymentOrder>? = null

    /** Null: succeed by marking the booking paid online. */
    var confirmResult: AppResult<Booking>? = null
    val confirmations = mutableListOf<Pair<String, CheckoutResult.Completed>>()

    var refreshCount = 0
        private set
    val createRequests = mutableListOf<BookingRequest>()
    val cancelledIds = mutableListOf<String>()

    fun setBookings(bookings: List<Booking>) = bookingsFlow.update { bookings }

    override val bookings: Flow<List<Booking>> = bookingsFlow

    override fun booking(id: String): Flow<Booking?> = bookingsFlow.map { list -> list.find { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCount++
        return refreshResult
    }

    override suspend fun createBooking(request: BookingRequest): AppResult<Booking> {
        createRequests += request
        val result = createResult ?: AppResult.Success(
            TestBookings.booking(
                id = "new-${createRequests.size}",
                slot = request.slot,
                serviceName = request.serviceName,
            ).copy(address = request.address, note = request.note, payment = BookingPayment(method = request.paymentMethod)),
        )
        if (result is AppResult.Success) bookingsFlow.update { it + result.data }
        return result
    }

    override suspend fun cancelBooking(bookingId: String): AppResult<Booking> {
        cancelledIds += bookingId
        val result = cancelResult ?: run {
            val booking = bookingsFlow.value.first { it.id == bookingId }
            AppResult.Success(booking.copy(status = BookingStatus.Cancelled))
        }
        if (result is AppResult.Success) {
            bookingsFlow.update { list -> list.map { if (it.id == bookingId) result.data else it } }
        }
        return result
    }

    override suspend fun submitReview(bookingId: String, input: ReviewInput): AppResult<Booking> {
        reviews += bookingId to input
        val result = reviewResult ?: run {
            val booking = bookingsFlow.value.first { it.id == bookingId }
            AppResult.Success(booking.copy(review = BookingReview(input.rating, input.comment, Instant.ofEpochMilli(0))))
        }
        if (result is AppResult.Success) {
            bookingsFlow.update { list -> list.map { if (it.id == bookingId) result.data else it } }
        }
        return result
    }

    override suspend fun isOnlinePaymentEnabled(): AppResult<Boolean> = AppResult.Success(onlinePaymentEnabled)

    override suspend fun startOnlinePayment(bookingId: String): AppResult<PaymentOrder> = orderResult ?: run {
        val booking = bookingsFlow.value.first { it.id == bookingId }
        AppResult.Success(PaymentOrder(bookingId, "order_$bookingId", booking.price, "rzp_test_fake", booking.serviceName))
    }

    override suspend fun confirmOnlinePayment(bookingId: String, result: CheckoutResult.Completed): AppResult<Booking> {
        confirmations += bookingId to result
        val outcome = confirmResult ?: run {
            val booking = bookingsFlow.value.first { it.id == bookingId }
            AppResult.Success(
                booking.copy(payment = booking.payment.copy(isPaid = true, via = PaymentMethod.Online, paidAt = Instant.EPOCH)),
            )
        }
        if (outcome is AppResult.Success) {
            bookingsFlow.update { list -> list.map { if (it.id == bookingId) outcome.data else it } }
        }
        return outcome
    }

    override suspend fun lastUsedAddress(): String? = lastAddress
}

/** Sample bookings for tests. */
object TestBookings {
    val date: LocalDate = LocalDate.of(2026, 11, 24)

    fun booking(
        id: String,
        status: BookingStatus = BookingStatus.Requested,
        slot: TimeSlot = TimeSlot(date, 11),
        serviceName: String = "Repair",
        completedAt: Instant? = null,
        review: BookingReview? = null,
        payment: BookingPayment = BookingPayment(),
    ) = Booking(
        id = id,
        worker = BookingWorker("w1", "Ramesh Kumar"),
        serviceName = serviceName,
        price = Paise.rupees(199),
        priceUnit = PriceUnit.PerVisit,
        slot = slot,
        address = "12 Main Road, Model Town",
        note = null,
        status = status,
        startCode = if (status == BookingStatus.Accepted) "4821" else null,
        createdAt = Instant.ofEpochMilli(0),
        completedAt = completedAt,
        review = review,
        payment = payment,
    )
}
