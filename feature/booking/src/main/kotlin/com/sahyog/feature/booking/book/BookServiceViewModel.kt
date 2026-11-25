package com.sahyog.feature.booking.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.repository.BookingRepository
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerService
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
import java.time.LocalDateTime

/** A time slot as offered in the picker. */
data class SlotOption(val slot: TimeSlot, val enabled: Boolean)

sealed interface BookServiceUiState {
    data object Loading : BookServiceUiState

    /** The worker left the catalog before the booking was made. */
    data object NotFound : BookServiceUiState

    data class Form(
        val worker: Worker,
        val selectedService: WorkerService?,
        val today: LocalDate,
        val dates: List<LocalDate>,
        val selectedDate: LocalDate,
        val slots: List<SlotOption>,
        val selectedSlot: TimeSlot?,
        val address: String,
        val note: String,
        val isSubmitting: Boolean,
        /** Pin from the map picker; null if the customer only typed the address. */
        val location: GeoPoint? = null,
    ) : BookServiceUiState {
        val addressIsValid: Boolean
            get() = address.trim().length >= BookingRequest.ADDRESS_MIN_LENGTH

        val canSubmit: Boolean
            get() = selectedService != null && selectedSlot != null && addressIsValid && !isSubmitting
    }
}

sealed interface BookServiceEvent {
    data class Booked(val bookingId: String) : BookServiceEvent

    data class Failed(val error: AppError) : BookServiceEvent
}

@HiltViewModel(assistedFactory = BookServiceViewModel.Factory::class)
class BookServiceViewModel @AssistedInject constructor(
    @Assisted private val workerId: String,
    catalogRepository: CatalogRepository,
    private val bookingRepository: BookingRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(workerId: String): BookServiceViewModel
    }

    private data class Input(
        val serviceName: String? = null,
        val date: LocalDate? = null,
        val slot: TimeSlot? = null,
        val address: String = "",
        val note: String = "",
        val isSubmitting: Boolean = false,
        val addressEdited: Boolean = false,
        val location: GeoPoint? = null,
    )

    private val input = MutableStateFlow(Input())

    private val events = Channel<BookServiceEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BookServiceEvent> = events.receiveAsFlow()

    val uiState: StateFlow<BookServiceUiState> = combine(
        catalogRepository.worker(workerId),
        input,
    ) { worker, input ->
        if (worker == null) return@combine BookServiceUiState.NotFound
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val dates = TimeSlot.bookableDates(today)
        // Default to the first day that still has a free slot (today may be over).
        val date = input.date ?: dates.firstOrNull { day -> TimeSlot.slotsFor(day, now).any { it.second } } ?: today
        val slots = TimeSlot.slotsFor(date, now).map { (slot, enabled) -> SlotOption(slot, enabled) }
        BookServiceUiState.Form(
            worker = worker,
            // Most workers list one main service: pre-select the first.
            selectedService = worker.services.find { it.name == input.serviceName } ?: worker.services.firstOrNull(),
            today = today,
            dates = dates,
            selectedDate = date,
            slots = slots,
            // A slot that has since passed is dropped rather than submitted.
            selectedSlot = input.slot?.takeIf { selected -> slots.any { it.slot == selected && it.enabled } },
            address = input.address,
            note = input.note,
            isSubmitting = input.isSubmitting,
            location = input.location,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookServiceUiState.Loading)

    init {
        viewModelScope.launch {
            val lastAddress = bookingRepository.lastUsedAddress() ?: return@launch
            // Don't overwrite anything the user already started typing.
            input.update { if (it.addressEdited) it else it.copy(address = lastAddress) }
        }
    }

    fun onServiceSelect(service: WorkerService) = input.update { it.copy(serviceName = service.name) }

    fun onDateSelect(date: LocalDate) = input.update {
        if (it.date == date) it else it.copy(date = date, slot = null)
    }

    fun onSlotSelect(slot: TimeSlot) = input.update { it.copy(date = slot.date, slot = slot) }

    fun onAddressChange(value: String) = input.update {
        it.copy(address = value.take(BookingRequest.ADDRESS_MAX_LENGTH), addressEdited = true)
    }

    /**
     * A pin from the map picker. Its address replaces the field only if the customer hasn't
     * typed one themselves (a pre-filled address from last time doesn't count).
     */
    fun onLocationPicked(point: GeoPoint, address: String?) = input.update {
        val replace = address != null && (!it.addressEdited || it.address.isBlank())
        it.copy(
            location = point,
            address = if (replace) address.take(BookingRequest.ADDRESS_MAX_LENGTH) else it.address,
            addressEdited = it.addressEdited || replace,
        )
    }

    fun onLocationClear() = input.update { it.copy(location = null) }

    fun onNoteChange(value: String) = input.update { it.copy(note = value.take(BookingRequest.NOTE_MAX_LENGTH)) }

    fun onSubmit() {
        val form = uiState.value as? BookServiceUiState.Form ?: return
        if (!form.canSubmit) return
        val service = form.selectedService ?: return
        val slot = form.selectedSlot ?: return
        input.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = bookingRepository.createBooking(
                BookingRequest(
                    workerId = workerId,
                    serviceName = service.name,
                    slot = slot,
                    address = form.address,
                    note = form.note.ifBlank { null },
                    location = form.location,
                ),
            )
            when (result) {
                is AppResult.Success -> events.send(BookServiceEvent.Booked(result.data.id))
                is AppResult.Failure -> {
                    val slotGone = result.error.let {
                        it is AppError.Validation &&
                            (it.field == BookingErrorFields.SLOT || it.field == BookingErrorFields.SLOT_TAKEN)
                    }
                    // The chosen slot can't be used any more: make the user pick again.
                    input.update { it.copy(isSubmitting = false, slot = if (slotGone) null else it.slot) }
                    events.send(BookServiceEvent.Failed(result.error))
                }
            }
        }
    }
}
