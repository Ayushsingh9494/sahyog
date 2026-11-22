package com.sahyog.core.data.booking

import kotlinx.serialization.Serializable

/**
 * The bookings API. Implemented by [DemoBookingDataSource] until the Spring Boot API
 * exists (Milestone 10). All calls act on behalf of the signed-in user; the server
 * identifies them from the auth token, never from a client-supplied id.
 *
 * Failures: `ApiException` for refused requests, `IOException` for connectivity.
 */
interface BookingNetworkDataSource {
    suspend fun fetchBookings(): List<NetworkBooking>

    suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking

    suspend fun cancelBooking(bookingId: String): NetworkBooking
}

/** Field names used in `AppError.Validation` for booking requests. */
object BookingErrorFields {
    const val WORKER = "worker"
    const val SERVICE = "service"
    const val SLOT = "slot"
    const val SLOT_TAKEN = "slot_taken"
    const val ADDRESS = "address"
    const val STATUS = "status"
}

/**
 * @property slotDate ISO date, e.g. "2026-11-24".
 * @property createdAtMillis Server time the booking was created.
 * @property customerName Shown to the worker; null in older records.
 * @property completedAtMillis When the worker marked the job done.
 */
@Serializable
data class NetworkBooking(
    val id: String,
    val workerId: String,
    val workerName: String,
    val serviceName: String,
    val pricePaise: Long,
    val priceUnit: String,
    val slotDate: String,
    val slotStartHour: Int,
    val address: String,
    val note: String? = null,
    val status: String,
    val startCode: String? = null,
    val createdAtMillis: Long,
    val customerName: String? = null,
    val completedAtMillis: Long? = null,
)

@Serializable
data class NetworkBookingRequest(
    val workerId: String,
    val serviceName: String,
    val slotDate: String,
    val slotStartHour: Int,
    val address: String,
    val note: String? = null,
)
