package com.sahyog.core.model

import java.time.Instant

/** How the customer means to pay, chosen when booking. Payment happens after the job. */
enum class PaymentMethod {
    /** Cash (or any direct payment) to the worker, who confirms it in the app. */
    Cash,

    /** UPI, netbanking or card through the payment gateway. */
    Online,
}

/**
 * A booking's payment. The amount is the booking's price.
 * @property via How it was actually paid; may differ from [method] (e.g. cash after choosing online).
 */
data class BookingPayment(
    val method: PaymentMethod = PaymentMethod.Cash,
    val isPaid: Boolean = false,
    val via: PaymentMethod? = null,
    val paidAt: Instant? = null,
)

/** Payment is due once the job is completed and until someone records it. */
val Booking.isPaymentDue: Boolean get() = status == BookingStatus.Completed && !payment.isPaid

val Job.isPaymentDue: Boolean get() = status == BookingStatus.Completed && !payment.isPaid

/** A gateway order, ready to open in the checkout. */
data class PaymentOrder(
    val bookingId: String,
    val orderId: String,
    val amount: Paise,
    val keyId: String,
    val description: String,
)

/** What the gateway's checkout returned. The server checks it before anything counts as paid. */
sealed interface CheckoutResult {
    data class Completed(val orderId: String, val paymentId: String, val signature: String) : CheckoutResult

    /** The customer closed the checkout. Nothing was charged. */
    data object Cancelled : CheckoutResult

    data class Failed(val reason: String?) : CheckoutResult
}
