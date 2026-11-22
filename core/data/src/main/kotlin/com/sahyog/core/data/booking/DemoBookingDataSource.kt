package com.sahyog.core.data.booking

import android.content.Context
import com.sahyog.core.common.dispatcher.Dispatcher
import com.sahyog.core.common.dispatcher.SahyogDispatcher
import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.catalog.CatalogNetworkDataSource
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.data.repository.AuthRepository
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.TimeSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * TEMPORARY pretend server for bookings, until the Spring Boot API exists (Milestone 10).
 *
 * It behaves like the real one should: bookings are stored per user (in a JSON file that
 * survives sign-out, like a server database), every request is validated, and a worker
 * "accepts" a new request after [AUTO_ACCEPT_SECONDS]. Real progress beyond Accepted
 * arrives with the worker app (M7).
 */
@Singleton
internal class DemoBookingDataSource(
    private val storeFile: File,
    private val authRepository: AuthRepository,
    private val catalog: CatalogNetworkDataSource,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val random: Random = Random.Default,
    private val latencyMs: Long = SIMULATED_LATENCY_MS,
) : BookingNetworkDataSource {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        authRepository: AuthRepository,
        catalog: CatalogNetworkDataSource,
        clock: Clock,
        @Dispatcher(SahyogDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.filesDir, "demo_server/bookings.json"), authRepository, catalog, clock, ioDispatcher)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchBookings(): List<NetworkBooking> = serverCall { store, uid ->
        val updated = store[uid].orEmpty().map { it.advanced() }
        store[uid] = updated
        updated
    }

    override suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking {
        // Looked up outside the lock: the catalog call has its own latency.
        val worker = catalog.fetchCatalog().workers.find { it.id == request.workerId }
            ?: throw ApiException(AppError.Validation(BookingErrorFields.WORKER))
        val service = worker.services.find { it.name == request.serviceName }
            ?: throw ApiException(AppError.Validation(BookingErrorFields.SERVICE))
        val slot = request.toSlot()
        if (!slot.isBookableAt(LocalDateTime.now(clock))) {
            throw ApiException(AppError.Validation(BookingErrorFields.SLOT))
        }
        val address = request.address.trim()
        if (address.length !in BookingRequest.ADDRESS_MIN_LENGTH..BookingRequest.ADDRESS_MAX_LENGTH) {
            throw ApiException(AppError.Validation(BookingErrorFields.ADDRESS))
        }

        return serverCall { store, uid ->
            val workerBusy = store.values.flatten().any {
                it.workerId == worker.id && it.slotDate == request.slotDate &&
                    it.slotStartHour == request.slotStartHour && it.status().isActive
            }
            if (workerBusy) throw ApiException(AppError.Validation(BookingErrorFields.SLOT_TAKEN))

            val booking = NetworkBooking(
                id = newBookingId(),
                workerId = worker.id,
                workerName = worker.name,
                serviceName = service.name,
                pricePaise = service.pricePaise,
                priceUnit = service.unit,
                slotDate = request.slotDate,
                slotStartHour = request.slotStartHour,
                address = address,
                note = request.note?.trim()?.take(BookingRequest.NOTE_MAX_LENGTH)?.ifBlank { null },
                status = BookingStatus.Requested.name,
                createdAtMillis = clock.millis(),
            )
            store[uid] = store[uid].orEmpty() + booking
            booking
        }
    }

    override suspend fun cancelBooking(bookingId: String): NetworkBooking = serverCall { store, uid ->
        val bookings = store[uid].orEmpty().map { it.advanced() }
        val booking = bookings.find { it.id == bookingId } ?: throw ApiException(AppError.NotFound)
        if (!booking.status().canCancel) throw ApiException(AppError.Validation(BookingErrorFields.STATUS))
        val cancelled = booking.copy(status = BookingStatus.Cancelled.name)
        store[uid] = bookings.map { if (it.id == bookingId) cancelled else it }
        cancelled
    }

    /** Applies what would have happened on the server since the booking was last read. */
    private fun NetworkBooking.advanced(): NetworkBooking {
        if (status() != BookingStatus.Requested) return this
        val slotStart = runCatching { LocalDate.parse(slotDate).atTime(slotStartHour, 0) }.getOrNull()
        return when {
            slotStart != null && !LocalDateTime.now(clock).isBefore(slotStart) ->
                copy(status = BookingStatus.Expired.name)
            clock.millis() - createdAtMillis >= AUTO_ACCEPT_SECONDS * 1_000 ->
                copy(status = BookingStatus.Accepted.name, startCode = newStartCode())
            else -> this
        }
    }

    /** Loads the store, runs [block] for the signed-in user and saves any changes, under one lock. */
    private suspend fun <T> serverCall(block: (MutableMap<String, List<NetworkBooking>>, String) -> T): T {
        delay(latencyMs)
        val uid = authRepository.session.first()?.uid ?: throw ApiException(AppError.Unauthorized)
        return withContext(ioDispatcher) {
            mutex.withLock {
                val store = load().toMutableMap()
                val result = block(store, uid)
                save(store)
                result
            }
        }
    }

    private fun load(): Map<String, List<NetworkBooking>> =
        if (storeFile.exists()) {
            runCatching { json.decodeFromString<Map<String, List<NetworkBooking>>>(storeFile.readText()) }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

    private fun save(store: Map<String, List<NetworkBooking>>) {
        storeFile.parentFile?.mkdirs()
        storeFile.writeText(json.encodeToString(store))
    }

    private fun NetworkBookingRequest.toSlot(): TimeSlot = try {
        TimeSlot(LocalDate.parse(slotDate), slotStartHour)
    } catch (e: DateTimeParseException) {
        throw ApiException(AppError.Validation(BookingErrorFields.SLOT))
    }

    private fun NetworkBooking.status() =
        BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.Requested

    /** e.g. "SH-7K3Q9P": short enough to read out over the phone. */
    private fun newBookingId(): String =
        "SH-" + (1..6).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")

    private fun newStartCode(): String = random.nextInt(1_000, 10_000).toString()

    companion object {
        const val AUTO_ACCEPT_SECONDS = 20L
        private const val SIMULATED_LATENCY_MS = 600L

        // No 0/O or 1/I, which are easy to confuse when read aloud.
        private const val ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
