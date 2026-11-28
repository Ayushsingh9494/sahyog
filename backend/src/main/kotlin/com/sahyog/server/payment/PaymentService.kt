package com.sahyog.server.payment

import com.sahyog.server.api.NotFoundException
import com.sahyog.server.api.ValidationException
import com.sahyog.server.booking.Booking
import com.sahyog.server.booking.BookingErrorFields
import com.sahyog.server.booking.BookingRepository
import com.sahyog.server.booking.BookingResponse
import com.sahyog.server.booking.BookingStatus
import com.sahyog.server.booking.PaymentMethod
import com.sahyog.server.booking.PaymentStatus
import com.sahyog.server.booking.toResponse
import com.sahyog.server.security.toCaller
import com.sahyog.server.user.Access
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

/** Whether the app may offer online payment, and the public key its checkout needs. */
data class PaymentConfig(val onlineEnabled: Boolean, val keyId: String?)

/** What the app's checkout opens. */
data class PaymentOrderResponse(
    val bookingId: String,
    val orderId: String,
    val amountPaise: Long,
    val currency: String,
    val keyId: String,
    val description: String,
)

/** The checkout's result, which the server checks with the gateway before believing it. */
data class PaymentVerifyRequest(val orderId: String, val paymentId: String, val signature: String)

/**
 * Payments happen after the job is completed, for the booking's price:
 * - online: the server opens a gateway order, the app pays it in the gateway's checkout, and the
 *   server marks it paid only after checking the signature AND asking the gateway itself;
 * - cash: the worker confirms they received it.
 */
@Service
class PaymentService(
    private val bookings: BookingRepository,
    private val gateway: PaymentGateway,
    private val access: Access,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun config() = PaymentConfig(gateway.isEnabled, gateway.publicKeyId.takeIf { gateway.isEnabled })

    @Transactional
    fun createOrder(uid: String, bookingId: String): PaymentOrderResponse {
        val booking = payable(customerBooking(uid, bookingId))
        if (!gateway.isEnabled) throw ValidationException(BookingErrorFields.PAYMENT)
        // Re-use an open order so retries don't pile up orders on the gateway.
        val orderId = booking.payment.gatewayOrderId ?: try {
            gateway.createOrder(booking.pricePaise, receipt = booking.id).id
        } catch (e: Exception) {
            log.warn("Could not create a payment order for {}", booking.id, e)
            throw ValidationException(BookingErrorFields.PAYMENT)
        }
        if (orderId != booking.payment.gatewayOrderId) bookings.update(booking.copy(payment = booking.payment.copy(gatewayOrderId = orderId)))
        return PaymentOrderResponse(
            bookingId = booking.id,
            orderId = orderId,
            amountPaise = booking.pricePaise,
            currency = "INR",
            keyId = gateway.publicKeyId,
            description = "${booking.serviceName} · ${booking.workerName}",
        )
    }

    @Transactional
    fun verify(uid: String, bookingId: String, request: PaymentVerifyRequest): BookingResponse {
        val booking = customerBooking(uid, bookingId)
        // Already recorded (e.g. the app retried after a lost response): same answer again.
        if (booking.payment.status == PaymentStatus.Paid && booking.payment.gatewayPaymentId == request.paymentId) {
            return booking.toResponse(forWorker = false)
        }
        payable(booking)
        val failed = ValidationException(BookingErrorFields.PAYMENT_FAILED)
        if (request.orderId != booking.payment.gatewayOrderId) throw failed
        if (!gateway.isSignatureValid(request.orderId, request.paymentId, request.signature)) throw failed

        // Don't take the app's word for it: ask the gateway what actually happened.
        var payment = try {
            gateway.fetchPayment(request.paymentId)
        } catch (e: Exception) {
            log.warn("Could not confirm payment {} for {}", request.paymentId, booking.id, e)
            throw failed
        }
        if (payment.orderId != request.orderId || payment.amountPaise != booking.pricePaise) throw failed
        if (payment.status == "authorized") payment = runCatching { gateway.capture(payment.id, booking.pricePaise) }.getOrNull() ?: throw failed
        if (payment.status != "captured") throw failed

        return save(booking, via = PaymentMethod.Online, gatewayPaymentId = payment.id).toResponse(forWorker = false)
    }

    /** The worker confirms they were paid in cash (works for any unpaid completed job). */
    @Transactional
    fun cashReceived(uid: String, jobId: String): BookingResponse {
        access.worker(uid)
        val job = bookings.findForUpdate(jobId)?.takeIf { it.workerUid == uid } ?: throw NotFoundException()
        return save(payable(job), via = PaymentMethod.Cash, gatewayPaymentId = null).toResponse(forWorker = true)
    }

    private fun customerBooking(uid: String, bookingId: String): Booking {
        access.user(uid)
        return bookings.findForUpdate(bookingId)?.takeIf { it.customerUid == uid } ?: throw NotFoundException()
    }

    /** Only a completed, unpaid booking can be paid. */
    private fun payable(booking: Booking): Booking {
        if (booking.status != BookingStatus.Completed || booking.payment.status == PaymentStatus.Paid) {
            throw ValidationException(BookingErrorFields.PAYMENT)
        }
        return booking
    }

    private fun save(booking: Booking, via: PaymentMethod, gatewayPaymentId: String?): Booking {
        val paid = booking.copy(
            payment = booking.payment.copy(
                status = PaymentStatus.Paid,
                via = via,
                paidAt = clock.instant(),
                gatewayPaymentId = gatewayPaymentId,
            ),
        )
        bookings.update(paid)
        return paid
    }
}

@RestController
class PaymentController(private val service: PaymentService) {

    @GetMapping("/api/v1/payments/config")
    fun config() = service.config()

    @PostMapping("/api/v1/bookings/{id}/payment/order")
    fun order(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) = service.createOrder(jwt.toCaller().uid, id)

    @PostMapping("/api/v1/bookings/{id}/payment/verify")
    fun verify(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String, @RequestBody request: PaymentVerifyRequest) =
        service.verify(jwt.toCaller().uid, id, request)

    @PostMapping("/api/v1/worker/jobs/{id}/cash-received")
    fun cashReceived(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: String) =
        service.cashReceived(jwt.toCaller().uid, id)
}
