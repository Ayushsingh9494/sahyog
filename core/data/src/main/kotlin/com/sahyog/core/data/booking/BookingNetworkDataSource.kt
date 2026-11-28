package com.sahyog.core.data.booking

import kotlinx.serialization.Serializable

/**
 * The bookings API (Sahyog server). All calls act on behalf of the signed-in user; the server
 * identifies them from the auth token, never from a client-supplied id.
 *
 * Failures: `ApiException` for refused requests, `IOException` for connectivity.
 */
interface BookingNetworkDataSource {
    suspend fun fetchBookings(): List<NetworkBooking>

    suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking

    suspend fun cancelBooking(bookingId: String): NetworkBooking

    /**
     * Rates a completed booking, once, within `ReviewInput.REVIEW_WINDOW` of completion.
     * Fails with [BookingErrorFields.REVIEW] for an invalid rating or comment, and
     * [BookingErrorFields.REVIEW_CLOSED] if the booking can't be reviewed (any more).
     */
    suspend fun reviewBooking(bookingId: String, review: NetworkReviewRequest): NetworkBooking

    /** Whether online payment is offered right now. */
    suspend fun paymentConfig(): NetworkPaymentConfig

    /** Opens (or re-uses) a gateway order for a completed, unpaid booking. */
    suspend fun createPaymentOrder(bookingId: String): NetworkPaymentOrder

    /** Hands the checkout's result to the server, which confirms it with the gateway. */
    suspend fun verifyPayment(bookingId: String, result: NetworkPaymentResult): NetworkBooking
}

@Serializable
data class NetworkPaymentConfig(val onlineEnabled: Boolean, val keyId: String? = null)

@Serializable
data class NetworkPaymentOrder(
    val bookingId: String,
    val orderId: String,
    val amountPaise: Long,
    val currency: String,
    val keyId: String,
    val description: String,
)

@Serializable
data class NetworkPaymentResult(val orderId: String, val paymentId: String, val signature: String)

/** Field names used in `AppError.Validation` for booking requests. */
object BookingErrorFields {
    const val WORKER = "worker"
    const val SERVICE = "service"
    const val SLOT = "slot"
    const val SLOT_TAKEN = "slot_taken"
    const val ADDRESS = "address"
    const val LOCATION = "location"
    const val STATUS = "status"
    const val REVIEW = "review"

    /** Not completed, already reviewed, or past the review window. */
    const val REVIEW_CLOSED = "review_closed"

    /** Online payment unavailable, or nothing to pay right now. */
    const val PAYMENT = "payment"

    /** The gateway didn't confirm the payment. */
    const val PAYMENT_FAILED = "payment_failed"
}

/**
 * @property slotDate ISO date, e.g. "2026-11-24".
 * @property createdAtMillis Server time the booking was created.
 * @property customerName Shown to the worker; null in older records.
 * @property completedAtMillis When the worker marked the job done.
 * @property review The customer's rating; visible to the customer and the worker.
 * @property latitude With [longitude], the customer's map pin; only ever sent to the customer
 *   and the assigned worker.
 */
@Serializable
data class NetworkBooking(
    val id: String,
    val workerId: String,
    val workerName: String,
    val serviceName: String,
    val pricePaise: Long,
    val priceUnit: String,
    val slotDate: String,
    val slotStartHour: Int,
    val address: String,
    val note: String? = null,
    val status: String,
    val startCode: String? = null,
    val createdAtMillis: Long,
    val customerName: String? = null,
    val completedAtMillis: Long? = null,
    val review: NetworkReview? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: String = "Cash",
    val paymentStatus: String = "Unpaid",
    val paidVia: String? = null,
    val paidAtMillis: Long? = null,
)

@Serializable
data class NetworkReview(
    val rating: Int,
    val comment: String? = null,
    val createdAtMillis: Long,
)

@Serializable
data class NetworkReviewRequest(
    val rating: Int,
    val comment: String? = null,
)

@Serializable
data class NetworkBookingRequest(
    val workerId: String,
    val serviceName: String,
    val slotDate: String,
    val slotStartHour: Int,
    val address: String,
    val note: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: String = "Cash",
)
