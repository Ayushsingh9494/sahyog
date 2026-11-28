package com.sahyog.core.data.booking

import com.sahyog.core.data.network.SahyogApi
import javax.inject.Inject

/** The customer's bookings on the Sahyog API; the server enforces every booking rule. */
internal class RetrofitBookingDataSource @Inject constructor(
    private val api: SahyogApi,
) : BookingNetworkDataSource {
    override suspend fun fetchBookings(): List<NetworkBooking> = api.bookings()

    override suspend fun createBooking(request: NetworkBookingRequest): NetworkBooking = api.createBooking(request)

    override suspend fun cancelBooking(bookingId: String): NetworkBooking = api.cancelBooking(bookingId)

    override suspend fun reviewBooking(bookingId: String, review: NetworkReviewRequest): NetworkBooking =
        api.reviewBooking(bookingId, review)

    override suspend fun paymentConfig(): NetworkPaymentConfig = api.paymentConfig()

    override suspend fun createPaymentOrder(bookingId: String): NetworkPaymentOrder = api.createPaymentOrder(bookingId)

    override suspend fun verifyPayment(bookingId: String, result: NetworkPaymentResult): NetworkBooking =
        api.verifyPayment(bookingId, result)
}
