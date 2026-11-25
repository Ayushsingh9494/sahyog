package com.sahyog.server.booking

import com.sahyog.server.worker.PriceUnit
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Same names and meaning as the app's `BookingStatus`. */
enum class BookingStatus {
    Requested, Accepted, WorkerEnRoute, InProgress, Completed, Cancelled, Rejected, Expired;

    val isLive: Boolean get() = this in LIVE

    companion object {
        val LIVE = setOf(Requested, Accepted, WorkerEnRoute, InProgress)
    }
}

/** Field names in validation errors, as in the app's `BookingErrorFields` / `WorkerErrorFields`. */
object BookingErrorFields {
    const val WORKER = "worker"
    const val SERVICE = "service"
    const val SLOT = "slot"
    const val SLOT_TAKEN = "slot_taken"
    const val ADDRESS = "address"
    const val LOCATION = "location"
    const val STATUS = "status"
    const val REVIEW = "review"
    const val REVIEW_CLOSED = "review_closed"
    const val START_CODE = "start_code"
    const val TOO_EARLY = "too_early"
}

/**
 * The booking rules, the same numbers as the app's `TimeSlot`, `BookingRequest`,
 * `JOB_START_WINDOW_HOURS` and `ReviewInput`.
 */
object BookingRules {
    /** Two-hour visit windows starting at these hours (9–11 ... 7–9 PM). */
    val START_HOURS = listOf(9, 11, 13, 15, 17, 19)
    const val MIN_LEAD_MINUTES = 60L
    const val BOOKING_WINDOW_DAYS = 7L
    val ADDRESS_LENGTH = 10..200
    const val NOTE_MAX_LENGTH = 300

    /** The worker may set off / start at most this long before the visit, so jobs can't be "done" early. */
    const val JOB_START_WINDOW_HOURS = 3L

    val RATING = 1..5
    const val COMMENT_MAX_LENGTH = 500
    const val REVIEW_WINDOW_DAYS = 14L

    fun isBookable(date: LocalDate, startHour: Int, now: LocalDateTime): Boolean {
        val start = date.atTime(startHour, 0)
        return startHour in START_HOURS &&
            !date.isAfter(now.toLocalDate().plusDays(BOOKING_WINDOW_DAYS - 1)) &&
            !start.isBefore(now.plusMinutes(MIN_LEAD_MINUTES))
    }
}

data class ReviewDto(val rating: Int, val comment: String?, val createdAtMillis: Long)

/** A booking as stored. */
data class Booking(
    val id: String,
    val customerUid: String,
    val workerUid: String,
    val customerName: String,
    val workerName: String,
    val serviceName: String,
    val pricePaise: Long,
    val priceUnit: PriceUnit,
    val slotDate: LocalDate,
    val slotStartHour: Int,
    val address: String,
    val note: String?,
    val latitude: Double?,
    val longitude: Double?,
    val status: BookingStatus,
    val startCode: String?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val review: ReviewDto?,
) {
    val slotStart: LocalDateTime get() = slotDate.atTime(slotStartHour, 0)
}

/**
 * The app's `NetworkBooking`. The same shape serves the customer (their bookings) and the
 * worker (their jobs), except that the worker never receives the start code.
 */
data class BookingResponse(
    val id: String,
    val workerId: String,
    val workerName: String,
    val serviceName: String,
    val pricePaise: Long,
    val priceUnit: PriceUnit,
    val slotDate: String,
    val slotStartHour: Int,
    val address: String,
    val note: String?,
    val status: BookingStatus,
    val startCode: String?,
    val createdAtMillis: Long,
    val customerName: String,
    val completedAtMillis: Long?,
    val review: ReviewDto?,
    val latitude: Double?,
    val longitude: Double?,
)

fun Booking.toResponse(forWorker: Boolean) = BookingResponse(
    id = id,
    workerId = workerUid,
    workerName = workerName,
    serviceName = serviceName,
    pricePaise = pricePaise,
    priceUnit = priceUnit,
    slotDate = slotDate.toString(),
    slotStartHour = slotStartHour,
    address = address,
    note = note,
    status = status,
    // The code proves the worker is at the door: only the customer may hold it.
    startCode = if (forWorker) null else startCode,
    createdAtMillis = createdAt.toEpochMilli(),
    customerName = customerName,
    completedAtMillis = completedAt?.toEpochMilli(),
    review = review,
    latitude = latitude,
    longitude = longitude,
)

@Repository
class BookingRepository(private val jdbc: JdbcClient) {

    fun forCustomer(uid: String): List<Booking> =
        jdbc.sql("SELECT * FROM bookings WHERE customer_uid = :uid ORDER BY slot_date, slot_start_hour, created_at")
            .param("uid", uid).query { rs, _ -> rs.toBooking() }.list()

    fun forWorker(uid: String): List<Booking> =
        jdbc.sql("SELECT * FROM bookings WHERE worker_uid = :uid ORDER BY slot_date, slot_start_hour, created_at")
            .param("uid", uid).query { rs, _ -> rs.toBooking() }.list()

    /** Locks the row until the transaction ends, so two actions on one booking can't interleave. */
    fun findForUpdate(id: String): Booking? =
        jdbc.sql("SELECT * FROM bookings WHERE id = :id FOR UPDATE")
            .param("id", id).query { rs, _ -> rs.toBooking() }.optional().orElse(null)

    fun exists(id: String): Boolean =
        jdbc.sql("SELECT count(*) FROM bookings WHERE id = :id").param("id", id).query(Long::class.java).single() > 0

    fun insert(b: Booking) {
        jdbc.sql(
            """
            INSERT INTO bookings (id, customer_uid, worker_uid, customer_name, worker_name, service_name,
                price_paise, price_unit, slot_date, slot_start_hour, address, note, latitude, longitude,
                status, created_at)
            VALUES (:id, :customer, :worker, :customerName, :workerName, :service, :price, :unit, :date, :hour,
                :address, :note, :lat, :lng, :status, :createdAt)
            """.trimIndent(),
        )
            .param("id", b.id).param("customer", b.customerUid).param("worker", b.workerUid)
            .param("customerName", b.customerName).param("workerName", b.workerName)
            .param("service", b.serviceName).param("price", b.pricePaise).param("unit", b.priceUnit.name)
            .param("date", b.slotDate).param("hour", b.slotStartHour)
            .param("address", b.address).param("note", b.note)
            .param("lat", b.latitude).param("lng", b.longitude)
            .param("status", b.status.name).param("createdAt", b.createdAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    fun update(b: Booking) {
        jdbc.sql(
            """
            UPDATE bookings SET status = :status, start_code = :code, completed_at = :completedAt,
                review_rating = :rating, review_comment = :comment, reviewed_at = :reviewedAt
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", b.id).param("status", b.status.name).param("code", b.startCode)
            .param("completedAt", b.completedAt?.atOffset(ZoneOffset.UTC))
            .param("rating", b.review?.rating).param("comment", b.review?.comment)
            .param("reviewedAt", b.review?.let { Instant.ofEpochMilli(it.createdAtMillis).atOffset(ZoneOffset.UTC) })
            .update()
    }

    /** Requests nobody accepted before the visit started; returns how many expired. */
    fun expireUnanswered(now: LocalDateTime): Int = jdbc.sql(
        """
        UPDATE bookings SET status = 'Expired'
        WHERE status = 'Requested' AND (slot_date + make_interval(hours => slot_start_hour)) <= :now
        """.trimIndent(),
    ).param("now", now).update()

    private fun ResultSet.toBooking(): Booking {
        val rating = getInt("review_rating").takeUnless { wasNull() }
        return Booking(
            id = getString("id"),
            customerUid = getString("customer_uid"),
            workerUid = getString("worker_uid"),
            customerName = getString("customer_name"),
            workerName = getString("worker_name"),
            serviceName = getString("service_name"),
            pricePaise = getLong("price_paise"),
            priceUnit = PriceUnit.valueOf(getString("price_unit")),
            slotDate = getObject("slot_date", LocalDate::class.java),
            slotStartHour = getInt("slot_start_hour"),
            address = getString("address"),
            note = getString("note"),
            latitude = getDouble("latitude").takeUnless { wasNull() },
            longitude = getDouble("longitude").takeUnless { wasNull() },
            status = BookingStatus.valueOf(getString("status")),
            startCode = getString("start_code"),
            createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
            completedAt = getObject("completed_at", OffsetDateTime::class.java)?.toInstant(),
            review = rating?.let {
                ReviewDto(
                    rating = it,
                    comment = getString("review_comment"),
                    createdAtMillis = getObject("reviewed_at", OffsetDateTime::class.java).toInstant().toEpochMilli(),
                )
            },
        )
    }
}
