package com.sahyog.feature.booking

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.model.WorkerService
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestBookingRepository
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.feature.booking.book.BookServiceEvent
import com.sahyog.feature.booking.book.BookServiceUiState
import com.sahyog.feature.booking.book.BookServiceViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BookServiceViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 23)

    /** 6 PM: only today's 7–9 PM slot is still bookable. */
    private fun clockAt(hour: Int): Clock =
        Clock.fixed(LocalDateTime.of(today, java.time.LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val fan = WorkerService("Fan installation", Paise.rupees(199), PriceUnit.PerVisit)
    private val wiring = WorkerService("Wiring", Paise.rupees(299), PriceUnit.PerHour)
    private val worker = TestCatalog.ramesh.copy(services = listOf(fan, wiring))

    private val catalog = TestCatalogRepository().apply { setCatalog(TestCatalog.categories, listOf(worker)) }
    private val bookings = TestBookingRepository()

    private fun viewModel(hour: Int = 18) = BookServiceViewModel(worker.id, catalog, bookings, clockAt(hour))

    @Test
    fun `defaults to the first service and the first day with free slots`() = runTest {
        // At 10 PM nothing is left today, so tomorrow is pre-selected.
        viewModel(hour = 22).uiState.test {
            val form = awaitForm()
            assertEquals(fan, form.selectedService)
            assertEquals(7, form.dates.size)
            assertEquals(today.plusDays(1), form.selectedDate)
            assertTrue(form.slots.all { it.enabled })
            assertNull(form.selectedSlot)
            assertFalse(form.canSubmit)
        }
    }

    @Test
    fun `slots too soon are disabled`() = runTest {
        viewModel(hour = 18).uiState.test {
            val form = awaitForm()
            assertEquals(today, form.selectedDate)
            assertEquals(listOf(19), form.slots.filter { it.enabled }.map { it.slot.startHour })
        }
    }

    @Test
    fun `address from the last booking is pre-filled`() = runTest {
        bookings.lastAddress = "12 Main Road, Model Town"
        viewModel().uiState.test {
            assertEquals("12 Main Road, Model Town", awaitForm { it.address.isNotEmpty() }.address)
        }
    }

    @Test
    fun `changing the day clears the chosen slot`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitForm()
            viewModel.onSlotSelect(TimeSlot(today, 19))
            assertEquals(TimeSlot(today, 19), awaitForm { it.selectedSlot != null }.selectedSlot)
            viewModel.onDateSelect(today.plusDays(2))
            assertNull(awaitForm { it.selectedDate == today.plusDays(2) }.selectedSlot)
        }
    }

    @Test
    fun `submits the request and reports the new booking`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitForm()
            viewModel.onServiceSelect(wiring)
            viewModel.onSlotSelect(TimeSlot(today.plusDays(1), 9))
            viewModel.onAddressChange("12 Main Road, Model Town")
            viewModel.onNoteChange("   ")
            assertTrue(awaitForm { it.canSubmit }.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.eventFlow.test {
            viewModel.onSubmit()
            assertEquals(BookServiceEvent.Booked("new-1"), awaitItem())
        }
        val request = bookings.createRequests.single()
        assertEquals("Wiring", request.serviceName)
        assertEquals(TimeSlot(today.plusDays(1), 9), request.slot)
        assertNull(request.note)
    }

    @Test
    fun `short address blocks submission`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitForm()
            viewModel.onSlotSelect(TimeSlot(today.plusDays(1), 9))
            viewModel.onAddressChange("Flat 2")
            val form = awaitForm { it.address == "Flat 2" }
            assertFalse(form.canSubmit)
        }
        viewModel.onSubmit()
        assertTrue(bookings.createRequests.isEmpty())
    }

    @Test
    fun `slot taken clears the slot so the user picks again`() = runTest {
        bookings.createResult = AppResult.Failure(AppError.Validation(BookingErrorFields.SLOT_TAKEN))
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitForm()
            viewModel.onSlotSelect(TimeSlot(today.plusDays(1), 9))
            viewModel.onAddressChange("12 Main Road, Model Town")
            awaitForm { it.canSubmit }
            viewModel.onSubmit()
            val form = awaitForm { !it.isSubmitting && it.selectedSlot == null }
            assertNull(form.selectedSlot)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.eventFlow.test {
            assertEquals(
                BookServiceEvent.Failed(AppError.Validation(BookingErrorFields.SLOT_TAKEN)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `worker missing from the catalog is not found`() = runTest {
        BookServiceViewModel("nobody", catalog, bookings, clockAt(18)).uiState.test {
            var item = awaitItem()
            if (item == BookServiceUiState.Loading) item = awaitItem()
            assertEquals(BookServiceUiState.NotFound, item)
        }
    }

    private suspend fun ReceiveTurbine<BookServiceUiState>.awaitForm(
        predicate: (BookServiceUiState.Form) -> Boolean = { true },
    ): BookServiceUiState.Form {
        while (true) {
            val item = awaitItem()
            if (item is BookServiceUiState.Form && predicate(item)) return item
        }
    }
}
