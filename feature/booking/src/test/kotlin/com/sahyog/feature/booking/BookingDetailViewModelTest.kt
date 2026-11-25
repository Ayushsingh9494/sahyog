package com.sahyog.feature.booking

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestBookingRepository
import com.sahyog.core.testing.TestBookings
import com.sahyog.feature.booking.detail.BookingDetailEvent
import com.sahyog.feature.booking.detail.BookingDetailUiState
import com.sahyog.feature.booking.detail.BookingDetailViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

class BookingDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(TestBookings.date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = TestBookingRepository().apply {
        setBookings(listOf(TestBookings.booking("b1", BookingStatus.Accepted)))
    }

    private fun viewModel(id: String = "b1") = BookingDetailViewModel(id, repository, clock)

    @Test
    fun `shows the booking and refreshes it on open`() = runTest {
        viewModel().uiState.test {
            val ready = awaitReady()
            assertEquals("b1", ready.booking.id)
            assertEquals("4821", ready.booking.startCode)
        }
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `unknown booking is not found`() = runTest {
        viewModel("nope").uiState.test {
            var item = awaitItem()
            if (item == BookingDetailUiState.Loading) item = awaitItem()
            assertEquals(BookingDetailUiState.NotFound, item)
        }
    }

    @Test
    fun `cancel updates the booking and confirms`() = runTest {
        val viewModel = viewModel()
        viewModel.eventFlow.test {
            viewModel.onCancel()
            assertEquals(BookingDetailEvent.Cancelled, awaitItem())
        }
        assertEquals(listOf("b1"), repository.cancelledIds)
        viewModel.uiState.test {
            val ready = awaitReady { it.booking.status == BookingStatus.Cancelled }
            assertFalse(ready.isCancelling)
        }
    }

    @Test
    fun `refused cancel reports why and re-fetches the booking`() = runTest {
        repository.cancelResult = AppResult.Failure(AppError.Validation(BookingErrorFields.STATUS))
        val viewModel = viewModel()
        viewModel.eventFlow.test {
            viewModel.onCancel()
            assertEquals(
                BookingDetailEvent.Failed(AppError.Validation(BookingErrorFields.STATUS)),
                awaitItem(),
            )
        }
        // One refresh on open, one after the refused cancel.
        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun `background refresh failures stay quiet`() = runTest {
        repository.refreshResult = AppResult.Failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.eventFlow.test {
            viewModel.onAutoRefresh()
            expectNoEvents()
        }
    }

    @Test
    fun `a completed booking can be rated, with the comment trimmed`() = runTest {
        repository.setBookings(listOf(completed()))
        val viewModel = viewModel("done")
        viewModel.uiState.test {
            val ready = awaitReady()
            assertTrue(ready.canReview)
            assertFalse(ready.review.canSubmit) // No stars yet.

            viewModel.onReviewRatingChange(4)
            viewModel.onReviewCommentChange("  Neat work  ")
            assertTrue(awaitReady { it.review.rating == 4 && it.review.comment.isNotEmpty() }.review.canSubmit)

            viewModel.onSubmitReview()
            val reviewed = awaitReady { it.booking.review != null }
            assertFalse(reviewed.canReview)
            assertEquals(4, reviewed.booking.review?.rating)
        }
        assertEquals(listOf("done" to ReviewInput(4, "Neat work")), repository.reviews)
    }

    @Test
    fun `no rating card for unfinished bookings or after the window`() = runTest {
        repository.setBookings(
            listOf(
                TestBookings.booking("open", BookingStatus.InProgress),
                completed().copy(id = "old", completedAt = clock.instant() - ReviewInput.REVIEW_WINDOW),
            ),
        )
        viewModel("open").uiState.test { assertFalse(awaitReady().canReview) }
        viewModel("old").uiState.test { assertFalse(awaitReady().canReview) }
    }

    @Test
    fun `refused rating keeps the draft and explains`() = runTest {
        repository.setBookings(listOf(completed()))
        repository.reviewResult = AppResult.Failure(AppError.Validation(BookingErrorFields.REVIEW_CLOSED))
        val viewModel = viewModel("done")
        viewModel.onReviewRatingChange(2)
        viewModel.eventFlow.test {
            viewModel.onSubmitReview()
            assertEquals(BookingDetailEvent.Failed(AppError.Validation(BookingErrorFields.REVIEW_CLOSED)), awaitItem())
        }
        viewModel.uiState.test {
            val ready = awaitReady()
            assertEquals(2, ready.review.rating)
            assertFalse(ready.review.isSubmitting)
        }
    }

    private fun completed() = TestBookings.booking(
        "done",
        BookingStatus.Completed,
        completedAt = clock.instant() - Duration.ofHours(2),
    )

    private suspend fun ReceiveTurbine<BookingDetailUiState>.awaitReady(
        predicate: (BookingDetailUiState.Ready) -> Boolean = { true },
    ): BookingDetailUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is BookingDetailUiState.Ready && predicate(item)) return item
        }
    }
}
