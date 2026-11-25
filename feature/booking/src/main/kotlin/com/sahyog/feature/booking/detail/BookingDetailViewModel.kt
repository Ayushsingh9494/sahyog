package com.sahyog.feature.booking.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.BookingRepository
import com.sahyog.core.model.Booking
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.model.canReview
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

sealed interface BookingDetailUiState {
    data object Loading : BookingDetailUiState

    data object NotFound : BookingDetailUiState

    /** @property canReview Completed, not rated yet, and still within the review window. */
    data class Ready(
        val booking: Booking,
        val today: LocalDate,
        val isRefreshing: Boolean,
        val isCancelling: Boolean,
        val canReview: Boolean = false,
        val review: ReviewDraft = ReviewDraft(),
    ) : BookingDetailUiState
}

/** The rating being written. [rating] is 0 until a star is tapped. */
data class ReviewDraft(
    val rating: Int = 0,
    val comment: String = "",
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && ReviewInput(rating, comment).isValid
}

sealed interface BookingDetailEvent {
    data object Cancelled : BookingDetailEvent

    data object Reviewed : BookingDetailEvent

    data class Failed(val error: AppError) : BookingDetailEvent
}

@HiltViewModel(assistedFactory = BookingDetailViewModel.Factory::class)
class BookingDetailViewModel @AssistedInject constructor(
    @Assisted private val bookingId: String,
    private val bookingRepository: BookingRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(bookingId: String): BookingDetailViewModel
    }

    private val refreshing = MutableStateFlow(false)
    private val cancelling = MutableStateFlow(false)
    private val draft = MutableStateFlow(ReviewDraft())

    private val events = Channel<BookingDetailEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BookingDetailEvent> = events.receiveAsFlow()

    val uiState: StateFlow<BookingDetailUiState> = combine(
        bookingRepository.booking(bookingId),
        refreshing,
        cancelling,
        draft,
    ) { booking, refreshing, cancelling, draft ->
        if (booking == null) {
            BookingDetailUiState.NotFound
        } else {
            BookingDetailUiState.Ready(
                booking = booking,
                today = LocalDate.now(clock),
                isRefreshing = refreshing,
                isCancelling = cancelling,
                canReview = booking.canReview(clock.instant()),
                review = draft,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingDetailUiState.Loading)

    init {
        refresh(userInitiated = false)
    }

    fun onRefresh() = refresh(userInitiated = true)

    /**
     * Called periodically by the screen while it's visible and the booking is still active,
     * until push notifications (M11) tell the app about status changes.
     */
    fun onAutoRefresh() = refresh(userInitiated = false)

    fun onCancel() {
        if (cancelling.value) return
        cancelling.value = true
        viewModelScope.launch {
            val result = bookingRepository.cancelBooking(bookingId)
            cancelling.value = false
            events.send(
                when (result) {
                    is AppResult.Success -> BookingDetailEvent.Cancelled
                    is AppResult.Failure -> BookingDetailEvent.Failed(result.error)
                },
            )
            // A refused cancel usually means the status moved on (e.g. the worker is
            // already on the way): fetch it so the screen shows why.
            if (result is AppResult.Failure) bookingRepository.refresh()
        }
    }

    fun onReviewRatingChange(rating: Int) = draft.update { it.copy(rating = rating) }

    fun onReviewCommentChange(comment: String) =
        draft.update { it.copy(comment = comment.take(ReviewInput.COMMENT_MAX_LENGTH)) }

    fun onSubmitReview() {
        val current = draft.value
        if (!current.canSubmit) return
        draft.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = bookingRepository.submitReview(
                bookingId,
                ReviewInput(current.rating, current.comment.trim().ifBlank { null }),
            )
            when (result) {
                is AppResult.Success -> {
                    draft.value = ReviewDraft()
                    events.send(BookingDetailEvent.Reviewed)
                }
                is AppResult.Failure -> {
                    // Keep what they wrote so they can try again.
                    draft.update { it.copy(isSubmitting = false) }
                    events.send(BookingDetailEvent.Failed(result.error))
                    // e.g. the window just closed: refetch so the card reflects it.
                    bookingRepository.refresh()
                }
            }
        }
    }

    private fun refresh(userInitiated: Boolean) {
        if (refreshing.value) return
        if (userInitiated) refreshing.value = true
        viewModelScope.launch {
            val result = bookingRepository.refresh()
            refreshing.value = false
            // Background refreshes fail quietly; the cached booking stays on screen.
            if (userInitiated && result is AppResult.Failure) events.send(BookingDetailEvent.Failed(result.error))
        }
    }
}
