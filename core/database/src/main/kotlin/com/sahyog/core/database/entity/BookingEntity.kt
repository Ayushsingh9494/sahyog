package com.sahyog.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sahyog.core.model.Booking
import com.sahyog.core.model.BookingReview
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.BookingPayment
import com.sahyog.core.model.PaymentMethod
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
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    @ColumnInfo(name = "review_rating") val reviewRating: Int? = null,
    @ColumnInfo(name = "review_comment") val reviewComment: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Instant? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    @ColumnInfo(name = "paid_via") val paidVia: String? = null,
    @ColumnInfo(name = "paid_at") val paidAt: Instant? = null,
)

/** The three review columns, read back as one value (all set, or none). */
internal fun reviewOf(rating: Int?, comment: String?, createdAt: Instant?): BookingReview? =
    if (rating != null && createdAt != null) BookingReview(rating, comment, createdAt) else null

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
    completedAt = completedAt,
    review = reviewOf(reviewRating, reviewComment, reviewedAt),
    location = GeoPoint.ofOrNull(latitude, longitude),
    payment = paymentOf(paymentMethod, paidVia, paidAt),
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
    completedAt = completedAt,
    reviewRating = review?.rating,
    reviewComment = review?.comment,
    reviewedAt = review?.createdAt,
    latitude = location?.latitude,
    longitude = location?.longitude,
    paymentMethod = payment.method.name,
    paidVia = payment.via?.name,
    paidAt = payment.paidAt,
)

/** Paid means both "how" and "when" are known. Unknown values (older rows) read as unpaid cash. */
internal fun paymentOf(method: String?, via: String?, paidAt: Instant?): BookingPayment {
    fun parse(value: String?) = PaymentMethod.entries.firstOrNull { it.name == value }
    val paidVia = parse(via)
    return BookingPayment(
        method = parse(method) ?: PaymentMethod.Cash,
        isPaid = paidVia != null && paidAt != null,
        via = paidVia,
        paidAt = paidAt,
    )
}
