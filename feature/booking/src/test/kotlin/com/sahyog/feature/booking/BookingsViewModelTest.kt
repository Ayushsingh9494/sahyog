package com.sahyog.feature.booking

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestBookingRepository
import com.sahyog.core.testing.TestBookings
import com.sahyog.feature.booking.list.BookingsEvent
import com.sahyog.feature.booking.list.BookingsTab
import com.sahyog.feature.booking.list.BookingsUiState
import com.sahyog.feature.booking.list.BookingsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

class BookingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(TestBookings.date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = TestBookingRepository().apply {
        setBookings(
            listOf(
                TestBookings.booking("upcoming-soon", BookingStatus.Accepted, TimeSlot(TestBookings.date, 9)),
                TestBookings.booking("upcoming-later", BookingStatus.Requested, TimeSlot(TestBookings.date.plusDays(2), 9)),
                TestBookings.booking("past-old", BookingStatus.Completed, TimeSlot(TestBookings.date.minusDays(5), 9)),
                TestBookings.booking("past-recent", BookingStatus.Cancelled, TimeSlot(TestBookings.date.minusDays(1), 9)),
            ),
        )
    }
    private val viewModel by lazy { BookingsViewModel(repository, clock) }

    @Test
    fun `upcoming tab shows active bookings soonest first`() = runTest {
        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("upcoming-soon", "upcoming-later"), state.bookings.map { it.id })
            assertEquals(2, state.upcomingCount)
        }
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `past tab shows finished bookings most recent first`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onTabSelect(BookingsTab.Past)
            val past = awaitLoaded { it.tab == BookingsTab.Past }
            assertEquals(listOf("past-recent", "past-old"), past.bookings.map { it.id })
        }
    }

    @Test
    fun `failed refresh is reported and cached bookings stay`() = runTest {
        repository.refreshResult = AppResult.Failure(AppError.Network)
        viewModel.eventFlow.test {
            assertEquals(BookingsEvent.RefreshFailed(AppError.Network), awaitItem())
        }
        viewModel.uiState.test { assertEquals(2, awaitLoaded().bookings.size) }
    }

    @Test
    fun `pull to refresh fetches again`() = runTest {
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onRefresh()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.refreshCount)
    }

    private suspend fun ReceiveTurbine<BookingsUiState>.awaitLoaded(
        predicate: (BookingsUiState) -> Boolean = { true },
    ): BookingsUiState {
        while (true) {
            val item = awaitItem()
            if (!item.isLoading && item.bookings.isNotEmpty() && predicate(item)) return item
        }
    }
}
