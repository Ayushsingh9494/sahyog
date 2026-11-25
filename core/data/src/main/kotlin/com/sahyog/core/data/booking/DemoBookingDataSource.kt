package com.sahyog.core.data.booking

import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.catalog.CatalogNetworkDataSource
import com.sahyog.core.data.demo.DemoServer
import com.sahyog.core.data.demo.StoredBooking
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.model.BookingRequest
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.ReviewInput
import com.sahyog.core.model.TimeSlot
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * TEMPORARY customer bookings API on top of [DemoServer], until the Spring Boot API
 * exists (Milestone 10). Validates requests the way the real server should.
 */
internal class DemoBookingDataSource @Inject constructor(
    private val server: DemoServer,
    private val catalog: CatalogNetworkDataSource,
) : BookingNetworkDataSource {

    override suspend fun fetchBookings(): List<NetworkBooking> = server.call { uid ->
        state.bookings.filter { it.customerUid == uid }.map { it.booking }
    }

    override suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking {
        // Looked up first, outside the server lock: the catalog is a request of its own.
        val worker = catalog.fetchCatalog().workers.find { it.id == request.workerId }
            ?: throw ApiException(AppError.Validation(BookingErrorFields.WORKER))
        val service = worker.services.find { it.name == request.serviceName }
            ?: throw ApiException(AppError.Validation(BookingErrorFields.SERVICE))
        val slot = request.toSlot()
        val address = request.address.trim()
        if (address.length !in BookingRequest.ADDRESS_MIN_LENGTH..BookingRequest.ADDRESS_MAX_LENGTH) {
            throw ApiException(AppError.Validation(BookingErrorFields.ADDRESS))
        }
        // Both coordinates or neither, and on Earth.
        val pin = when {
            request.latitude == null && request.longitude == null -> null
            else -> GeoPoint.ofOrNull(request.latitude, request.longitude)?.doorstep()
                ?: throw ApiException(AppError.Validation(BookingErrorFields.LOCATION))
        }

        return server.call { uid ->
            if (!slot.isBookableAt(now)) throw ApiException(AppError.Validation(BookingErrorFields.SLOT))
            // Workers can't book themselves.
            if (worker.id == uid) throw ApiException(AppError.Validation(BookingErrorFields.WORKER))
            val workerBusy = state.bookings.any {
                it.booking.workerId == worker.id && it.booking.slotDate == request.slotDate &&
                    it.booking.slotStartHour == request.slotStartHour && it.booking.status().isActive
            }
            if (workerBusy) throw ApiException(AppError.Validation(BookingErrorFields.SLOT_TAKEN))

            val booking = NetworkBooking(
                id = server.newBookingId(),
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
                createdAtMillis = nowMillis,
                customerName = userName(uid),
                latitude = pin?.latitude,
                longitude = pin?.longitude,
            )
            state = state.copy(bookings = state.bookings + StoredBooking(uid, booking))
            booking
        }
    }

    override suspend fun cancelBooking(bookingId: String): NetworkBooking = server.call { uid ->
        val stored = state.bookings.find { it.customerUid == uid && it.booking.id == bookingId }
            ?: throw ApiException(AppError.NotFound)
        if (!stored.booking.status().canCancel) {
            throw ApiException(AppError.Validation(BookingErrorFields.STATUS))
        }
        val cancelled = stored.booking.copy(status = BookingStatus.Cancelled.name)
        state = state.copy(
            bookings = state.bookings.map { if (it.booking.id == bookingId) it.copy(booking = cancelled) else it },
        )
        cancelled
    }

    override suspend fun reviewBooking(bookingId: String, review: NetworkReviewRequest): NetworkBooking =
        server.call { uid ->
            val comment = review.comment?.trim()?.ifBlank { null }
            if (!ReviewInput(review.rating, comment).isValid) {
                throw ApiException(AppError.Validation(BookingErrorFields.REVIEW))
            }
            // Only the customer who booked it can see (and so review) the booking.
            val stored = state.bookings.find { it.customerUid == uid && it.booking.id == bookingId }
                ?: throw ApiException(AppError.NotFound)
            val booking = stored.booking
            val completedAt = booking.completedAtMillis
            val open = booking.status() == BookingStatus.Completed && booking.review == null &&
                completedAt != null && nowMillis - completedAt < ReviewInput.REVIEW_WINDOW.toMillis()
            if (!open) throw ApiException(AppError.Validation(BookingErrorFields.REVIEW_CLOSED))

            val reviewed = booking.copy(review = NetworkReview(review.rating, comment, nowMillis))
            state = state.copy(
                bookings = state.bookings.map { if (it.booking.id == bookingId) it.copy(booking = reviewed) else it },
            )
            reviewed
        }

    private fun NetworkBookingRequest.toSlot(): TimeSlot = try {
        TimeSlot(LocalDate.parse(slotDate), slotStartHour)
    } catch (e: DateTimeParseException) {
        throw ApiException(AppError.Validation(BookingErrorFields.SLOT))
    }
}

internal fun NetworkBooking.status() =
    BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.Requested
