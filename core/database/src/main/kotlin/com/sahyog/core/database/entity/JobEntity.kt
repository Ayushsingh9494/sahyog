package com.sahyog.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.Job
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.TimeSlot
import java.time.Instant
import java.time.LocalDate

/**
 * Cached copy of a job assigned to the signed-in worker. Holds customers' names and
 * addresses, so the table is cleared on sign-out.
 */
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "customer_name") val customerName: String?,
    @ColumnInfo(name = "service_name") val serviceName: String,
    @ColumnInfo(name = "price_paise") val pricePaise: Long,
    @ColumnInfo(name = "price_unit") val priceUnit: String,
    @ColumnInfo(name = "slot_date") val slotDate: LocalDate,
    @ColumnInfo(name = "slot_start_hour") val slotStartHour: Int,
    val address: String,
    val note: String?,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "review_rating") val reviewRating: Int? = null,
    @ColumnInfo(name = "review_comment") val reviewComment: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Instant? = null,
)

fun JobEntity.asExternalModel() = Job(
    id = id,
    customerName = customerName,
    serviceName = serviceName,
    price = Paise(pricePaise),
    priceUnit = PriceUnit.entries.firstOrNull { it.name == priceUnit } ?: PriceUnit.PerVisit,
    slot = TimeSlot(slotDate, slotStartHour),
    address = address,
    note = note,
    status = BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.Requested,
    createdAt = createdAt,
    completedAt = completedAt,
    review = reviewOf(reviewRating, reviewComment, reviewedAt),
)

fun Job.asEntity() = JobEntity(
    id = id,
    customerName = customerName,
    serviceName = serviceName,
    pricePaise = price.value,
    priceUnit = priceUnit.name,
    slotDate = slot.date,
    slotStartHour = slot.startHour,
    address = address,
    note = note,
    status = status.name,
    createdAt = createdAt,
    completedAt = completedAt,
    reviewRating = review?.rating,
    reviewComment = review?.comment,
    reviewedAt = review?.createdAt,
)
