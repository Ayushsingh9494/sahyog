package com.sahyog.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.entity.BookingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BookingDaoTest {

    private lateinit var database: SahyogDatabase
    private lateinit var dao: BookingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SahyogDatabase::class.java,
        ).build()
        dao = database.bookingDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun ordersBySoonestVisitAndRoundTripsTypes() = runTest {
        val today = LocalDate.of(2026, 11, 23)
        dao.upsertAll(
            listOf(
                booking("b1", today.plusDays(1), 9, createdAt = 1_000),
                booking("b2", today, 15, createdAt = 2_000),
                booking("b3", today, 11, createdAt = 3_000),
            ),
        )
        val bookings = dao.observeBookings().first()
        assertEquals(listOf("b3", "b2", "b1"), bookings.map { it.id })
        assertEquals(today, bookings.first().slotDate)
        assertEquals(Instant.ofEpochMilli(3_000), bookings.first().createdAt)
    }

    @Test
    fun lastUsedAddressIsFromTheNewestBooking() = runTest {
        assertNull(dao.lastUsedAddress())
        dao.upsert(booking("b1", LocalDate.of(2026, 11, 25), 9, createdAt = 1_000, address = "Old flat, Model Town"))
        dao.upsert(booking("b2", LocalDate.of(2026, 11, 24), 9, createdAt = 2_000, address = "New flat, Civil Lines"))
        assertEquals("New flat, Civil Lines", dao.lastUsedAddress())
    }

    @Test
    fun upsertUpdatesStatusAndReplaceAllDropsMissing() = runTest {
        val date = LocalDate.of(2026, 11, 24)
        dao.upsertAll(listOf(booking("b1", date, 9), booking("b2", date, 11)))
        dao.upsert(booking("b1", date, 9).copy(status = "Cancelled"))
        assertEquals("Cancelled", dao.observeBooking("b1").first()?.status)

        dao.replaceAll(listOf(booking("b2", date, 11)))
        assertEquals(listOf("b2"), dao.observeBookings().first().map { it.id })

        dao.deleteAll()
        assertEquals(emptyList<Any>(), dao.observeBookings().first())
    }

    private fun booking(
        id: String,
        date: LocalDate,
        hour: Int,
        createdAt: Long = 0,
        address: String = "12 Main Road, Model Town",
    ) = BookingEntity(
        id = id,
        workerId = "w1",
        workerName = "Ramesh Kumar",
        serviceName = "Fan installation",
        pricePaise = 19_900,
        priceUnit = "PerVisit",
        slotDate = date,
        slotStartHour = hour,
        address = address,
        note = null,
        status = "Requested",
        startCode = null,
        createdAt = Instant.ofEpochMilli(createdAt),
    )
}
