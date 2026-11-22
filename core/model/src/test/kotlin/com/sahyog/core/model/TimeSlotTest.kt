package com.sahyog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TimeSlotTest {

    private val today = LocalDate.of(2026, 11, 23)

    @Test
    fun `offers seven days starting today`() {
        val dates = TimeSlot.bookableDates(today)
        assertEquals(7, dates.size)
        assertEquals(today, dates.first())
        assertEquals(today.plusDays(6), dates.last())
    }

    @Test
    fun `slots starting within the lead time are not bookable`() {
        val now = LocalDateTime.of(today, java.time.LocalTime.of(10, 30))
        val bookable = TimeSlot.slotsFor(today, now).associate { it.first.startHour to it.second }
        assertEquals(mapOf(9 to false, 11 to false, 13 to true, 15 to true, 17 to true, 19 to true), bookable)
    }

    @Test
    fun `exactly the lead time ahead is bookable`() {
        val now = LocalDateTime.of(today, java.time.LocalTime.of(12, 0))
        assertTrue(TimeSlot(today, 13).isBookableAt(now))
    }

    @Test
    fun `beyond the window, past days and unknown hours are rejected`() {
        val now = LocalDateTime.of(today, java.time.LocalTime.of(8, 0))
        assertFalse(TimeSlot(today.plusDays(7), 9).isBookableAt(now))
        assertTrue(TimeSlot(today.plusDays(6), 19).isBookableAt(now))
        assertFalse(TimeSlot(today.minusDays(1), 19).isBookableAt(now))
        assertFalse(TimeSlot(today, 10).isBookableAt(now))
    }

    @Test
    fun `slot spans two hours`() {
        val slot = TimeSlot(today, 17)
        assertEquals(LocalDateTime.of(today, java.time.LocalTime.of(19, 0)), slot.end)
    }

    @Test
    fun `only requested and accepted bookings can be cancelled`() {
        assertEquals(
            setOf(BookingStatus.Requested, BookingStatus.Accepted),
            BookingStatus.entries.filter { it.canCancel }.toSet(),
        )
        assertTrue(BookingStatus.InProgress.isActive)
        assertFalse(BookingStatus.Cancelled.isActive)
    }
}
