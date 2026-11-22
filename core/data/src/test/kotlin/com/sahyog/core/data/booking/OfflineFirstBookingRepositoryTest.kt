package com.sahyog.core.data.booking

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.entity.BookingEntity
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class OfflineFirstBookingRepositoryTest {

    private val dao = FakeBookingDao()
    private val network = FakeNetwork()
    private val repository = OfflineFirstBookingRepository(dao, network)

    private val request = BookingRequest(
        workerId = "w1",
        serviceName = "Fan installation",
        slot = TimeSlot(LocalDate.of(2026, 11, 24), 11),
        address = " 12 Main Road, Model Town ",
        note = " ",
    )

    @Test
    fun `create sends a trimmed request and caches the confirmed booking`() = runTest {
        val result = repository.createBooking(request)

        assertEquals("12 Main Road, Model Town", network.lastRequest?.address)
        assertEquals(null, network.lastRequest?.note)
        assertEquals("2026-11-24", network.lastRequest?.slotDate)
        val booking = (result as AppResult.Success).data
        assertEquals(listOf(booking), repository.bookings.first())
        assertEquals(TimeSlot(LocalDate.of(2026, 11, 24), 11), booking.slot)
    }

    @Test
    fun `refused create caches nothing and surfaces the reason`() = runTest {
        network.failure = ApiException(AppError.Validation(BookingErrorFields.SLOT_TAKEN))
        assertEquals(
            AppResult.Failure(AppError.Validation(BookingErrorFields.SLOT_TAKEN)),
            repository.createBooking(request),
        )
        assertTrue(repository.bookings.first().isEmpty())
    }

    @Test
    fun `refresh replaces the cache with the server list`() = runTest {
        repository.createBooking(request)
        network.serverBookings = listOf(network.serverBookings.single().copy(status = "Accepted", startCode = "4821"))

        assertEquals(AppResult.Success(Unit), repository.refresh())
        val booking = repository.bookings.first().single()
        assertEquals(BookingStatus.Accepted, booking.status)
        assertEquals("4821", booking.startCode)
    }

    @Test
    fun `offline refresh keeps the cache`() = runTest {
        repository.createBooking(request)
        network.failure = IOException()
        assertEquals(AppResult.Failure(AppError.Network), repository.refresh())
        assertEquals(1, repository.bookings.first().size)
    }

    @Test
    fun `cancel updates the cached booking`() = runTest {
        val booking = (repository.createBooking(request) as AppResult.Success).data
        repository.cancelBooking(booking.id)
        assertEquals(BookingStatus.Cancelled, repository.booking(booking.id).first()?.status)
    }

    @Test
    fun `sign-out cleanup empties the cache`() = runTest {
        repository.createBooking(request)
        repository.clearUserData()
        assertTrue(repository.bookings.first().isEmpty())
    }

    private class FakeNetwork : BookingNetworkDataSource {
        var failure: Exception? = null
        var lastRequest: NetworkBookingRequest? = null
        var serverBookings = emptyList<NetworkBooking>()

        override suspend fun fetchBookings(): List<NetworkBooking> {
            failure?.let { throw it }
            return serverBookings
        }

        override suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking {
            failure?.let { throw it }
            lastRequest = request
            val booking = NetworkBooking(
                id = "SH-ABC234", workerId = request.workerId, workerName = "Ramesh Kumar",
                serviceName = request.serviceName, pricePaise = 19_900, priceUnit = "PerVisit",
                slotDate = request.slotDate, slotStartHour = request.slotStartHour,
                address = request.address, note = request.note, status = "Requested", createdAtMillis = 1,
            )
            serverBookings = serverBookings + booking
            return booking
        }

        override suspend fun cancelBooking(bookingId: String): NetworkBooking {
            failure?.let { throw it }
            val cancelled = serverBookings.first { it.id == bookingId }.copy(status = "Cancelled")
            serverBookings = serverBookings.map { if (it.id == bookingId) cancelled else it }
            return cancelled
        }
    }

    private class FakeBookingDao : BookingDao {
        private val rows = MutableStateFlow<Map<String, BookingEntity>>(emptyMap())

        override fun observeBookings(): Flow<List<BookingEntity>> = rows.map { it.values.toList() }
        override fun observeBooking(id: String): Flow<BookingEntity?> = rows.map { it[id] }
        override suspend fun lastUsedAddress(): String? = rows.value.values.maxByOrNull { it.createdAt }?.address
        override suspend fun upsert(booking: BookingEntity) {
            rows.value = rows.value + (booking.id to booking)
        }
        override suspend fun upsertAll(bookings: List<BookingEntity>) = bookings.forEach { upsert(it) }
        override suspend fun deleteAll() {
            rows.value = emptyMap()
        }
        override suspend fun replaceAll(bookings: List<BookingEntity>) {
            rows.value = bookings.associateBy { it.id }
        }
    }
}
