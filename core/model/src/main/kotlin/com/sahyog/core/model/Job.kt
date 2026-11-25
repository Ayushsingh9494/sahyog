package com.sahyog.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A booking as the assigned worker sees it. Same booking, same id and status as the
 * customer's [Booking], but with the customer's details instead of the worker's.
 *
 * @property customerName As registered; null for very old records.
 * @property completedAt When the worker marked it done; null until then.
 * @property review The customer's rating of this job, once they give one.
 */
data class Job(
    val id: String,
    val customerName: String?,
    val serviceName: String,
    val price: Paise,
    val priceUnit: PriceUnit,
    val slot: TimeSlot,
    val address: String,
    val note: String?,
    val status: BookingStatus,
    val createdAt: Instant,
    val completedAt: Instant?,
    val review: BookingReview? = null,
)

/** How long before the visit the worker may set off or start (so jobs can't be "done" early). */
const val JOB_START_WINDOW_HOURS = 3L

/** What the worker can do next with a job; the server enforces the same rules. */
enum class JobAction { Accept, Decline, StartTrip, StartJob, Complete }

fun BookingStatus.allowedJobActions(): Set<JobAction> = when (this) {
    BookingStatus.Requested -> setOf(JobAction.Accept, JobAction.Decline)
    BookingStatus.Accepted -> setOf(JobAction.StartTrip)
    BookingStatus.WorkerEnRoute -> setOf(JobAction.StartJob)
    BookingStatus.InProgress -> setOf(JobAction.Complete)
    else -> emptySet()
}

/**
 * Totals of completed jobs. Amounts are the agreed starting prices; actual payments are
 * recorded once online payment exists (M12).
 */
data class EarningsSummary(
    val thisWeek: Paise,
    val thisWeekCount: Int,
    val thisMonth: Paise,
    val thisMonthCount: Int,
    val allTime: Paise,
    val allTimeCount: Int,
) {
    companion object {
        /** Weeks start on Monday; "this month" is the calendar month, both in [zone]. */
        fun of(jobs: List<Job>, today: LocalDate, zone: ZoneId): EarningsSummary {
            val done = jobs.filter { it.status == BookingStatus.Completed }
            fun dayOf(job: Job): LocalDate = (job.completedAt ?: job.createdAt).atZone(zone).toLocalDate()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = done.filter { !dayOf(it).isBefore(weekStart) && !dayOf(it).isAfter(today) }
            val month = done.filter { dayOf(it).year == today.year && dayOf(it).month == today.month }
            fun List<Job>.total() = Paise(sumOf { it.price.value })
            return EarningsSummary(
                thisWeek = week.total(),
                thisWeekCount = week.size,
                thisMonth = month.total(),
                thisMonthCount = month.size,
                allTime = done.total(),
                allTimeCount = done.size,
            )
        }
    }
}
