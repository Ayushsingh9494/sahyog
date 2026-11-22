package com.sahyog.feature.booking.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.BookingRepository
import com.sahyog.core.model.Booking
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
import javax.inject.Inject

enum class BookingsTab { Upcoming, Past }

data class BookingsUiState(
    val tab: BookingsTab = BookingsTab.Upcoming,
    /** Bookings for the selected tab: upcoming soonest first, past most recent first. */
    val bookings: List<Booking> = emptyList(),
    val upcomingCount: Int = 0,
    val today: LocalDate,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

sealed interface BookingsEvent {
    data class RefreshFailed(val error: AppError) : BookingsEvent
}

@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val tab = MutableStateFlow(BookingsTab.Upcoming)
    private val refreshing = MutableStateFlow(false)
    private val firstLoadDone = MutableStateFlow(false)

    private val events = Channel<BookingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BookingsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<BookingsUiState> = combine(
        bookingRepository.bookings,
        tab,
        refreshing,
        firstLoadDone,
    ) { bookings, tab, refreshing, loaded ->
        val (upcoming, past) = bookings.partition { it.status.isActive }
        BookingsUiState(
            tab = tab,
            bookings = when (tab) {
                BookingsTab.Upcoming -> upcoming
                BookingsTab.Past -> past.sortedWith(compareByDescending<Booking> { it.slot.start }.thenByDescending { it.createdAt })
            },
            upcomingCount = upcoming.size,
            today = LocalDate.now(clock),
            // Cached bookings show at once; the spinner is only for an empty first load.
            isLoading = !loaded && bookings.isEmpty(),
            isRefreshing = refreshing,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BookingsUiState(today = LocalDate.now(clock)),
    )

    init {
        refresh(userInitiated = false)
    }

    fun onTabSelect(value: BookingsTab) = tab.update { value }

    fun onRefresh() = refresh(userInitiated = true)

    private fun refresh(userInitiated: Boolean) {
        if (refreshing.value) return
        if (userInitiated) refreshing.value = true
        viewModelScope.launch {
            val result = bookingRepository.refresh()
            refreshing.value = false
            firstLoadDone.value = true
            if (result is AppResult.Failure) events.send(BookingsEvent.RefreshFailed(result.error))
        }
    }
}
