package com.sahyog.core.data.network

import com.sahyog.core.data.booking.NetworkBooking
import com.sahyog.core.data.booking.NetworkBookingRequest
import com.sahyog.core.data.booking.NetworkReviewRequest
import com.sahyog.core.data.catalog.NetworkCatalog
import com.sahyog.core.data.review.NetworkWorkerReview
import com.sahyog.core.data.worker.NetworkWorkerProfile
import com.sahyog.core.data.worker.NetworkWorkerProfileInput
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * The Sahyog server's API (see backend/README.md). Every call carries the Firebase ID token
 * ([AuthInterceptor]); a refused call throws `retrofit2.HttpException`, which [apiCall] turns
 * into an `AppError` using the server's `{code, field}` body.
 */
internal interface SahyogApi {

    // --- Me ---

    /** 404 means signed in but not onboarded yet. */
    @GET("api/v1/me")
    suspend fun me(): Response<NetworkUser>

    @PUT("api/v1/me")
    suspend fun saveMe(@Body request: NetworkProfileRequest): NetworkUser

    // --- Catalog and reviews ---

    @GET("api/v1/catalog")
    suspend fun catalog(): NetworkCatalog

    @GET("api/v1/workers/{id}/reviews")
    suspend fun reviews(@Path("id") workerId: String): List<NetworkWorkerReview>

    // --- Customer bookings ---

    @GET("api/v1/bookings")
    suspend fun bookings(): List<NetworkBooking>

    @POST("api/v1/bookings")
    suspend fun createBooking(@Body request: NetworkBookingRequest): NetworkBooking

    @POST("api/v1/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String): NetworkBooking

    @POST("api/v1/bookings/{id}/review")
    suspend fun reviewBooking(@Path("id") id: String, @Body review: NetworkReviewRequest): NetworkBooking

    // --- Worker ---

    /** 204 (no body) when the worker hasn't filled in the profile yet. */
    @GET("api/v1/worker/profile")
    suspend fun workerProfile(): Response<NetworkWorkerProfile>

    @PUT("api/v1/worker/profile")
    suspend fun saveWorkerProfile(@Body input: NetworkWorkerProfileInput): NetworkWorkerProfile

    @PUT("api/v1/worker/profile/availability")
    suspend fun setAvailability(@Body request: NetworkAvailability): NetworkWorkerProfile

    @Multipart
    @POST("api/v1/worker/profile/document")
    suspend fun submitDocument(@Part("type") type: RequestBody, @Part image: MultipartBody.Part): NetworkWorkerProfile

    @GET("api/v1/worker/jobs")
    suspend fun jobs(): List<NetworkBooking>

    /** [action]: accept, decline, start-trip or complete. */
    @POST("api/v1/worker/jobs/{id}/{action}")
    suspend fun jobAction(@Path("id") id: String, @Path("action") action: String): NetworkBooking

    @POST("api/v1/worker/jobs/{id}/start")
    suspend fun startJob(@Path("id") id: String, @Body request: NetworkStartJob): NetworkBooking
}

/** The signed-in person's profile as the server returns it. */
@Serializable
internal data class NetworkUser(
    val uid: String,
    val phoneNumber: String,
    val name: String,
    val roles: Set<String>,
    val workerStatus: String? = null,
)

@Serializable
internal data class NetworkProfileRequest(val name: String, val roles: Set<String>)

@Serializable
internal data class NetworkAvailability(val available: Boolean)

@Serializable
internal data class NetworkStartJob(val startCode: String)

/** The server's error body: `{"code": "validation", "field": "address"}`. */
@Serializable
internal data class NetworkError(val code: String? = null, val field: String? = null)
