package com.sahyog.server.booking

import com.sahyog.server.api.ForbiddenException
import com.sahyog.server.api.NotFoundException
import com.sahyog.server.api.ValidationException
import com.sahyog.server.payment.PaymentGateway
import com.sahyog.server.user.Access
import com.sahyog.server.user.Role
import com.sahyog.server.user.WorkerStatus
import com.sahyog.server.worker.LatLng
import com.sahyog.server.worker.WorkerRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.math.round

/** What the customer submits (the app's `NetworkBookingRequest`). */
data class BookingRequest(
    val workerId: String,
    val serviceName: String,
    val slotDate: String,
    val slotStartHour: Int,
    val address: String,
    val note: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Cash on delivery unless the customer chose to pay online. */
    val paymentMethod: PaymentMethod = PaymentMethod.Cash,
)

data class ReviewRequest(val rating: Int, val comment: String? = null)

data class StartJobRequest(val startCode: String)

/** What the worker can do next, per status (the app's `allowedJobActions`). */
enum class JobAction(vararg val from: BookingStatus) {
    Accept(BookingStatus.Requested),
    Decline(BookingStatus.Requested),
    StartTrip(BookingStatus.Accepted),
    StartJob(BookingStatus.WorkerEnRoute),
    Complete(BookingStatus.InProgress),
}

@Service
class BookingService(
    private val bookings: BookingRepository,
    private val workers: WorkerRepository,
    private val access: Access,
    private val clock: Clock,
    private val gateway: PaymentGateway,
    private val demo: DemoMode,
) {
    private val random = SecureRandom()

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    // --- Customer ---

    @Transactional
    fun customerBookings(uid: String): List<BookingResponse> {
        access.user(uid)
        expireUnanswered()
        return bookings.forCustomer(uid).map { it.toResponse(forWorker = false) }
    }

    @Transactional
    fun create(uid: String, request: BookingRequest): BookingResponse {
        val customer = access.user(uid)
        if (Role.Customer !in customer.roles) throw ForbiddenException()

        val worker = workers.find(request.workerId)
        if (worker == null || worker.status != WorkerStatus.Verified.name || !worker.isAvailable || worker.uid == uid) {
            throw ValidationException(BookingErrorFields.WORKER)
        }
        val service = worker.services.find { it.name == request.serviceName }
            ?: throw ValidationException(BookingErrorFields.SERVICE)
        val date = try {
            LocalDate.parse(request.slotDate)
        } catch (e: DateTimeParseException) {
            throw ValidationException(BookingErrorFields.SLOT)
        }
        if (!BookingRules.isBookable(date, request.slotStartHour, now())) throw ValidationException(BookingErrorFields.SLOT)
        val address = request.address.trim()
        if (address.length !in BookingRules.ADDRESS_LENGTH) throw ValidationException(BookingErrorFields.ADDRESS)
        val pin = when {
            request.latitude == null && request.longitude == null -> null
            request.latitude == null || request.longitude == null -> throw ValidationException(BookingErrorFields.LOCATION)
            else -> LatLng(request.latitude, request.longitude).takeIf { it.isValid }
                ?.let { LatLng(it.latitude.round5(), it.longitude.round5()) }
                ?: throw ValidationException(BookingErrorFields.LOCATION)
        }

        // Online payment is only offered while the gateway is configured.
        if (request.paymentMethod == PaymentMethod.Online && !gateway.isEnabled) {
            throw ValidationException(BookingErrorFields.PAYMENT)
        }

        val booking = Booking(
            id = newBookingId(),
            customerUid = uid,
            workerUid = worker.uid,
            customerName = customer.name,
            workerName = worker.name,
            serviceName = service.name,
            pricePaise = service.pricePaise,
            priceUnit = service.unit,
            slotDate = date,
            slotStartHour = request.slotStartHour,
            address = address,
            note = request.note?.trim()?.take(BookingRules.NOTE_MAX_LENGTH)?.ifBlank { null },
            latitude = pin?.latitude,
            longitude = pin?.longitude,
            status = BookingStatus.Requested,
            startCode = null,
            createdAt = clock.instant(),
            completedAt = null,
            review = null,
            payment = Payment(method = request.paymentMethod),
        )
        try {
            bookings.insert(booking)
        } catch (e: DuplicateKeyException) {
            // The partial unique index: someone else holds a live booking for this worker and slot.
            throw ValidationException(BookingErrorFields.SLOT_TAKEN)
        }
        return booking.toResponse(forWorker = false)
    }

    /** Allowed only before the worker sets off. */
    @Transactional
    fun cancel(uid: String, bookingId: String): BookingResponse {
        val booking = customerBooking(uid, bookingId)
        if (booking.status != BookingStatus.Requested && booking.status != BookingStatus.Accepted) {
            throw ValidationException(BookingErrorFields.STATUS)
        }
        return save(booking.copy(status = BookingStatus.Cancelled)).toResponse(forWorker = false)
    }

    /** Once per completed booking, within the review window; never edited afterwards. */
    @Transactional
    fun review(uid: String, bookingId: String, request: ReviewRequest): BookingResponse {
        val comment = request.comment?.trim()?.ifBlank { null }
        if (request.rating !in BookingRules.RATING || (comment?.length ?: 0) > BookingRules.COMMENT_MAX_LENGTH) {
            throw ValidationException(BookingErrorFields.REVIEW)
        }
        val booking = customerBooking(uid, bookingId)
        val completedAt = booking.completedAt
        val open = booking.status == BookingStatus.Completed && booking.review == null && completedAt != null &&
            Duration.between(completedAt, clock.instant()) < Duration.ofDays(BookingRules.REVIEW_WINDOW_DAYS)
        if (!open) throw ValidationException(BookingErrorFields.REVIEW_CLOSED)
        val reviewed = booking.copy(review = ReviewDto(request.rating, comment, clock.millis()))
        return save(reviewed).toResponse(forWorker = false)
    }

    // --- Worker ---

    @Transactional
    fun workerJobs(uid: String): List<BookingResponse> {
        access.worker(uid)
        expireUnanswered()
        return bookings.forWorker(uid).map { it.toResponse(forWorker = true) }
    }

    @Transactional
    fun act(uid: String, jobId: String, action: JobAction, startCode: String? = null): BookingResponse {
        access.worker(uid)
        expireUnanswered()
        val job = bookings.findForUpdate(jobId)?.takeIf { it.workerUid == uid } ?: throw NotFoundException()
        // e.g. the customer cancelled while the worker was looking at the request.
        if (job.status !in action.from) throw ValidationException(BookingErrorFields.STATUS)
        if ((action == JobAction.StartTrip || action == JobAction.StartJob) && !demo.relaxStartWindow) {
            if (now().isBefore(job.slotStart.minusHours(BookingRules.JOB_START_WINDOW_HOURS))) {
                throw ValidationException(BookingErrorFields.TOO_EARLY)
            }
        }
        val updated = when (action) {
            JobAction.Accept -> job.copy(status = BookingStatus.Accepted, startCode = newStartCode())
            JobAction.Decline -> job.copy(status = BookingStatus.Rejected)
            JobAction.StartTrip -> job.copy(status = BookingStatus.WorkerEnRoute)
            JobAction.StartJob -> {
                if (startCode?.trim() != job.startCode) throw ValidationException(BookingErrorFields.START_CODE)
                job.copy(status = BookingStatus.InProgress)
            }
            JobAction.Complete -> job.copy(status = BookingStatus.Completed, completedAt = clock.instant())
        }
        return save(updated).toResponse(forWorker = true)
    }

    // --- Shared ---

    /** Requests nobody answered expire when their visit time arrives. Also runs every minute. */
    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    fun expireUnanswered() {
        bookings.expireUnanswered(now())
    }

    private fun customerBooking(uid: String, bookingId: String): Booking {
        access.user(uid)
        expireUnanswered()
        // Someone else's booking is "not found", not "forbidden": its existence isn't revealed.
        return bookings.findForUpdate(bookingId)?.takeIf { it.customerUid == uid } ?: throw NotFoundException()
    }

    private fun save(booking: Booking): Booking = booking.also(bookings::update)

    /** e.g. "SH-7K3Q9P": short enough to read out over the phone. */
    private fun newBookingId(): String {
        while (true) {
            val id = "SH-" + (1..6).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")
            if (!bookings.exists(id)) return id
        }
    }

    private fun newStartCode(): String = (1_000 + random.nextInt(9_000)).toString()

    private fun Double.round5() = round(this * 100_000) / 100_000

    private companion object {
        // No 0/O or 1/I, which are easy to confuse when read aloud.
        const val ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
