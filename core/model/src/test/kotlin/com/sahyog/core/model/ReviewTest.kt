package com.sahyog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReviewTest {

    private val doneAt = Instant.parse("2026-11-23T10:00:00Z")
    private val booking = Booking(
        id = "b1",
        worker = BookingWorker("w1", "Ramesh Kumar"),
        serviceName = "Fan installation",
        price = Paise.rupees(199),
        priceUnit = PriceUnit.PerVisit,
        slot = TimeSlot(LocalDate.of(2026, 11, 23), 9),
        address = "12 Main Road",
        note = null,
        status = BookingStatus.Completed,
        startCode = null,
        createdAt = Instant.EPOCH,
        completedAt = doneAt,
    )

    @Test
    fun `completed bookings can be reviewed once, within the window`() {
        assertTrue(booking.canReview(doneAt.plusSeconds(60)))
        assertFalse(booking.canReview(doneAt + ReviewInput.REVIEW_WINDOW))
        assertFalse(booking.copy(review = BookingReview(5, null, doneAt)).canReview(doneAt))
        assertFalse(booking.copy(status = BookingStatus.InProgress, completedAt = null).canReview(doneAt))
    }

    @Test
    fun `input needs 1 to 5 stars and a comment that isn't too long`() {
        assertTrue(ReviewInput(1, null).isValid)
        assertTrue(ReviewInput(5, "x".repeat(ReviewInput.COMMENT_MAX_LENGTH)).isValid)
        assertFalse(ReviewInput(0, null).isValid)
        assertFalse(ReviewInput(6, null).isValid)
        assertFalse(ReviewInput(4, "x".repeat(ReviewInput.COMMENT_MAX_LENGTH + 1)).isValid)
    }

    @Test
    fun `public names are first names only`() {
        assertEquals("Asha", publicFirstName("  Asha   Devi Sharma "))
        assertEquals("Ravi", publicFirstName("Ravi"))
        assertNull(publicFirstName("   "))
        assertNull(publicFirstName(null))
    }
}
