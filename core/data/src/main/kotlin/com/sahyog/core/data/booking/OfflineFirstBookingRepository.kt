package com.sahyog.core.data.booking

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.network.apiCall
import com.sahyog.core.data.repository.BookingRepository
import com.sahyog.core.data.session.UserDataCleaner
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.entity.asEntity
import com.sahyog.core.database.entity.asExternalModel
import com.sahyog.core.model.Booking
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.BookingReview
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.BookingWorker
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads come from the Room cache (works offline). Writes (create, cancel) go to the
 * server first and are cached only once it confirms them, so the app never shows a
 * booking the cooperative doesn't know about.
 */
@Singleton
internal class OfflineFirstBookingRepository @Inject constructor(
    private val dao: BookingDao,
    private val network: BookingNetworkDataSource,
) : BookingRepository, UserDataCleaner {

    override val bookings: Flow<List<Booking>> = dao.observeBookings()
        .map { entities -> entities.map { it.asExternalModel() } }
        .distinctUntilChanged()

    override fun booking(id: String): Flow<Booking?> = dao.observeBooking(id)
        .map { it?.asExternalModel() }
        .distinctUntilChanged()

    override suspend fun refresh(): AppResult<Unit> = apiCall {
        val bookings = network.fetchBookings().map { it.asExternalModel() }
        dao.replaceAll(bookings.map { it.asEntity() })
    }

    override suspend fun createBooking(request: BookingRequest): AppResult<Booking> = apiCall {
        val created = network.createBooking(
            NetworkBookingRequest(
                workerId = request.workerId,
                serviceName = request.serviceName,
                slotDate = request.slot.date.toString(),
                slotStartHour = request.slot.startHour,
                address = request.address.trim(),
                note = request.note?.trim()?.ifBlank { null },
            ),
        ).asExternalModel()
        dao.upsert(created.asEntity())
        created
    }

    override suspend fun cancelBooking(bookingId: String): AppResult<Booking> = apiCall {
        val cancelled = network.cancelBooking(bookingId).asExternalModel()
        dao.upsert(cancelled.asEntity())
        cancelled
    }

    override suspend fun submitReview(bookingId: String, input: ReviewInput): AppResult<Booking> = apiCall {
        val reviewed = network.reviewBooking(
            bookingId,
            NetworkReviewRequest(rating = input.rating, comment = input.comment?.trim()?.ifBlank { null }),
        ).asExternalModel()
        dao.upsert(reviewed.asEntity())
        reviewed
    }

    override suspend fun lastUsedAddress(): String? = dao.lastUsedAddress()

    override suspend fun clearUserData() = dao.deleteAll()
}

internal fun NetworkBooking.asExternalModel() = Booking(
    id = id,
    worker = BookingWorker(id = workerId, name = workerName),
    serviceName = serviceName,
    price = Paise(pricePaise),
    priceUnit = PriceUnit.entries.firstOrNull { it.name == priceUnit } ?: PriceUnit.PerVisit,
    slot = TimeSlot(LocalDate.parse(slotDate), slotStartHour),
    address = address,
    note = note,
    status = BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.Requested,
    startCode = startCode,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    completedAt = completedAtMillis?.let(Instant::ofEpochMilli),
    review = review?.asExternalModel(),
)

internal fun NetworkReview.asExternalModel() = BookingReview(
    rating = rating,
    comment = comment,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)
