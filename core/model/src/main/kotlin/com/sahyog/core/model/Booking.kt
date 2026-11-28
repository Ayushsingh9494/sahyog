package com.sahyog.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Where a booking is in its life. Transitions are enforced by the server:
 *
 * ```
 * Requested -> Accepted -> WorkerEnRoute -> InProgress -> Completed
 *     |-> Rejected / Expired      Requested or Accepted -> Cancelled
 * ```
 */
enum class BookingStatus {
    Requested,
    Accepted,
    WorkerEnRoute,
    InProgress,
    Completed,
    Cancelled,
    Rejected,
    Expired,
    ;

    /** Still going to happen (shown under "Upcoming"). */
    val isActive: Boolean
        get() = this in ACTIVE

    /** The customer may cancel without involving the cooperative. */
    val canCancel: Boolean
        get() = this == Requested || this == Accepted

    private companion object {
        val ACTIVE = setOf(Requested, Accepted, WorkerEnRoute, InProgress)
    }
}

/**
 * A two-hour visit window on a given day.
 * @property startHour 24-hour clock, e.g. 9 for 9–11 AM.
 */
data class TimeSlot(val date: LocalDate, val startHour: Int) {
    val start: LocalDateTime get() = date.atTime(startHour, 0)
    val end: LocalDateTime get() = start.plusHours(LENGTH_HOURS)
    val startTime: LocalTime get() = LocalTime.of(startHour, 0)
    val endTime: LocalTime get() = startTime.plusHours(LENGTH_HOURS)

    companion object {
        const val LENGTH_HOURS = 2L

        /** Window starts offered every day: 9–11, 11–1, 1–3, 3–5, 5–7, 7–9. */
        val START_HOURS = listOf(9, 11, 13, 15, 17, 19)

        /** How far ahead a slot must start, so the worker has time to accept and travel. */
        const val MIN_LEAD_MINUTES = 60L

        /** Days customers can book ahead, including today. */
        const val BOOKING_WINDOW_DAYS = 7

        /** The days shown in the date picker, starting today. */
        fun bookableDates(today: LocalDate): List<LocalDate> =
            (0 until BOOKING_WINDOW_DAYS).map { today.plusDays(it.toLong()) }

        /** All slots on [date], with whether each can still be booked at [now]. */
        fun slotsFor(date: LocalDate, now: LocalDateTime): List<Pair<TimeSlot, Boolean>> =
            START_HOURS.map { hour ->
                val slot = TimeSlot(date, hour)
                slot to slot.isBookableAt(now)
            }
    }

    fun isBookableAt(now: LocalDateTime): Boolean {
        val lastDay = now.toLocalDate().plusDays(BOOKING_WINDOW_DAYS - 1L)
        return startHour in START_HOURS &&
            !date.isAfter(lastDay) &&
            !start.isBefore(now.plusMinutes(MIN_LEAD_MINUTES))
    }
}

/** The worker as recorded on the booking (a snapshot: later profile changes don't rewrite history). */
data class BookingWorker(
    val id: String,
    val name: String,
)

/**
 * A customer's booking of one service from one worker.
 *
 * @property price The service's starting price at the time of booking.
 * @property startCode Shown to the customer once the worker accepts; the worker enters it
 *   to start the job, proving they're actually at the customer's place.
 * @property completedAt When the worker marked the job done; null until then.
 * @property review The customer's rating, once given.
 * @property location Where the customer dropped the pin, if they did; shared only with the worker.
 * @property payment How it will be / was paid; paid after the job.
 */
data class Booking(
    val id: String,
    val worker: BookingWorker,
    val serviceName: String,
    val price: Paise,
    val priceUnit: PriceUnit,
    val slot: TimeSlot,
    val address: String,
    val note: String?,
    val status: BookingStatus,
    val startCode: String?,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val review: BookingReview? = null,
    val location: GeoPoint? = null,
    val payment: BookingPayment = BookingPayment(),
)

/** What the customer submits; the server fills in price, status and ids. */
data class BookingRequest(
    val workerId: String,
    val serviceName: String,
    val slot: TimeSlot,
    val address: String,
    val note: String?,
    /** Optional pin from the map picker, so the worker can navigate to the door. */
    val location: GeoPoint? = null,
    /** Cash to the worker, or online after the job. */
    val paymentMethod: PaymentMethod = PaymentMethod.Cash,
) {
    companion object {
        const val ADDRESS_MIN_LENGTH = 10
        const val ADDRESS_MAX_LENGTH = 200
        const val NOTE_MAX_LENGTH = 300
    }
}
