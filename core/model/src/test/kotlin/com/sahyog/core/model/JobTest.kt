package com.sahyog.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class JobTest {

    // Wednesday 25 Nov 2026: the week started Monday 23 Nov.
    private val today = LocalDate.of(2026, 11, 25)

    private fun job(rupees: Long, completedOn: LocalDate?, status: BookingStatus = BookingStatus.Completed) = Job(
        id = "j$rupees",
        customerName = "Asha",
        serviceName = "Repair",
        price = Paise.rupees(rupees),
        priceUnit = PriceUnit.PerVisit,
        slot = TimeSlot(today, 9),
        address = "12 Main Road",
        note = null,
        status = status,
        createdAt = Instant.EPOCH,
        completedAt = completedOn?.atTime(12, 0)?.toInstant(ZoneOffset.UTC),
    )

    @Test
    fun `earnings group completed jobs by week, month and all time`() {
        val jobs = listOf(
            job(100, today), // this week
            job(200, LocalDate.of(2026, 11, 23)), // Monday: this week
            job(400, LocalDate.of(2026, 11, 22)), // Sunday before: this month only
            job(800, LocalDate.of(2026, 10, 31)), // last month
            job(1_600, today, BookingStatus.InProgress), // not done: ignored
        )
        val summary = EarningsSummary.of(jobs, today, ZoneOffset.UTC)
        assertEquals(Paise.rupees(300), summary.thisWeek)
        assertEquals(2, summary.thisWeekCount)
        assertEquals(Paise.rupees(700), summary.thisMonth)
        assertEquals(Paise.rupees(1_500), summary.allTime)
        assertEquals(4, summary.allTimeCount)
        // Nothing paid yet: all of it is still to collect.
        assertEquals(Paise.rupees(1_500), summary.toCollect)
    }

    @Test
    fun `earnings split what was received in cash and online from what is still owed`() {
        fun paid(job: Job, via: PaymentMethod) = job.copy(payment = BookingPayment(isPaid = true, via = via, paidAt = Instant.EPOCH))
        val jobs = listOf(
            paid(job(100, today), PaymentMethod.Cash),
            paid(job(200, today), PaymentMethod.Online),
            job(400, today),
            paid(job(800, today, BookingStatus.Cancelled), PaymentMethod.Cash), // not a completed job: ignored
        )
        val summary = EarningsSummary.of(jobs, today, ZoneOffset.UTC)
        assertEquals(Paise.rupees(100), summary.receivedCash)
        assertEquals(Paise.rupees(200), summary.receivedOnline)
        assertEquals(Paise.rupees(400), summary.toCollect)
    }

    @Test
    fun `payment is due only on a completed, unpaid job`() {
        assertEquals(true, job(100, today).isPaymentDue)
        assertEquals(false, job(100, today, BookingStatus.InProgress).isPaymentDue)
        assertEquals(false, job(100, today).copy(payment = BookingPayment(isPaid = true, via = PaymentMethod.Cash)).isPaymentDue)
    }

    @Test
    fun `each status allows only the next step`() {
        assertEquals(setOf(JobAction.Accept, JobAction.Decline), BookingStatus.Requested.allowedJobActions())
        assertEquals(setOf(JobAction.StartTrip), BookingStatus.Accepted.allowedJobActions())
        assertEquals(setOf(JobAction.StartJob), BookingStatus.WorkerEnRoute.allowedJobActions())
        assertEquals(setOf(JobAction.Complete), BookingStatus.InProgress.allowedJobActions())
        assertEquals(emptySet<JobAction>(), BookingStatus.Completed.allowedJobActions())
        assertEquals(emptySet<JobAction>(), BookingStatus.Cancelled.allowedJobActions())
    }
}
