package com.sahyog.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sahyog.core.model.Booking
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.BookingWorker
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.TimeSlot
import java.time.Instant
import java.time.LocalDate

/**
 * Cached copy of one of the signed-in customer's bookings. Holds personal data
 * (address), so the whole table is cleared on sign-out.
 *
 * The worker is a snapshot (id + name), not a foreign key to `workers`: bookings must
 * survive the worker leaving the catalog.
 */
@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "worker_name") val workerName: String,
    @ColumnInfo(name = "service_name") val serviceName: String,
    @ColumnInfo(name = "price_paise") val pricePaise: Long,
    @ColumnInfo(name = "price_unit") val priceUnit: String,
    @ColumnInfo(name = "slot_date") val slotDate: LocalDate,
    @ColumnInfo(name = "slot_start_hour") val slotStartHour: Int,
    val address: String,
    val note: String?,
    val status: String,
    @ColumnInfo(name = "start_code") val startCode: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

fun BookingEntity.asExternalModel() = Booking(
    id = id,
    worker = BookingWorker(id = workerId, name = workerName),
    serviceName = serviceName,
    price = Paise(pricePaise),
    priceUnit = PriceUnit.entries.firstOrNull { it.name == priceUnit } ?: PriceUnit.PerVisit,
    slot = TimeSlot(slotDate, slotStartHour),
    address = address,
    note = note,
    // An unknown status from a newer server is shown as Requested rather than crashing.
    status = BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.Requested,
    startCode = startCode,
    createdAt = createdAt,
)

fun Booking.asEntity() = BookingEntity(
    id = id,
    workerId = worker.id,
    workerName = worker.name,
    serviceName = serviceName,
    pricePaise = price.value,
    priceUnit = priceUnit.name,
    slotDate = slot.date,
    slotStartHour = slot.startHour,
    address = address,
    note = note,
    status = status.name,
    startCode = startCode,
    createdAt = createdAt,
)
