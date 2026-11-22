package com.sahyog.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sahyog.core.database.entity.BookingEntity
import kotlinx.coroutines.flow.Flow

/** Offline cache of the signed-in customer's bookings. */
@Dao
interface BookingDao {

    /** Soonest visit first; ties broken by newest booking. */
    @Query("SELECT * FROM bookings ORDER BY slot_date, slot_start_hour, created_at DESC")
    fun observeBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE id = :id")
    fun observeBooking(id: String): Flow<BookingEntity?>

    /** The address typed most recently, to pre-fill the next booking. */
    @Query("SELECT address FROM bookings ORDER BY created_at DESC LIMIT 1")
    suspend fun lastUsedAddress(): String?

    @Upsert
    suspend fun upsert(booking: BookingEntity)

    /** Makes the cache match the server's full list, removing bookings it no longer returns. */
    @Transaction
    suspend fun replaceAll(bookings: List<BookingEntity>) {
        deleteAll()
        upsertAll(bookings)
    }

    @Upsert
    suspend fun upsertAll(bookings: List<BookingEntity>)

    @Query("DELETE FROM bookings")
    suspend fun deleteAll()
}
