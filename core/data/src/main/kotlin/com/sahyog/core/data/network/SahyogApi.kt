package com.sahyog.core.data.network

import com.sahyog.core.data.booking.NetworkBooking
import com.sahyog.core.data.booking.NetworkBookingRequest
import com.sahyog.core.data.booking.NetworkPaymentConfig
import com.sahyog.core.data.booking.NetworkPaymentOrder
import com.sahyog.core.data.booking.NetworkPaymentResult
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

    // --- Payments ---

    @GET("api/v1/payments/config")
    suspend fun paymentConfig(): NetworkPaymentConfig

    @POST("api/v1/bookings/{id}/payment/order")
    suspend fun createPaymentOrder(@Path("id") id: String): NetworkPaymentOrder

    @POST("api/v1/bookings/{id}/payment/verify")
    suspend fun verifyPayment(
        @Path("id") id: String,
        @Body result: NetworkPaymentResult,
    ): NetworkBooking

    @POST("api/v1/worker/jobs/{id}/cash-received")
    suspend fun cashReceived(@Path("id") id: String): NetworkBooking

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

    // --- Admin (cooperative staff only; 403 for everyone else) ---

    @GET("api/v1/admin/overview")
    suspend fun adminOverview(): NetworkAdminOverview

    @GET("api/v1/admin/verifications")
    suspend fun verifications(): List<NetworkVerificationRequest>

    /** The ID image bytes; never cached (the server sends `no-store`). */
    @GET("api/v1/admin/verifications/{uid}/document")
    suspend fun document(@Path("uid") uid: String): okhttp3.ResponseBody

    @POST("api/v1/admin/verifications/{uid}/approve")
    suspend fun approve(@Path("uid") uid: String)

    @POST("api/v1/admin/verifications/{uid}/reject")
    suspend fun reject(@Path("uid") uid: String, @Body request: NetworkReason)

    @GET("api/v1/admin/workers")
    suspend fun adminWorkers(
        @retrofit2.http.Query("status") status: String?,
        @retrofit2.http.Query("q") query: String?,
    ): List<NetworkAdminWorker>

    @GET("api/v1/admin/workers/{uid}")
    suspend fun adminWorker(@Path("uid") uid: String): NetworkAdminWorkerDetail

    @POST("api/v1/admin/workers/{uid}/revoke")
    suspend fun revoke(@Path("uid") uid: String, @Body request: NetworkReason)
}

@Serializable
internal data class NetworkReason(val reason: String)

@Serializable
internal data class NetworkStatusChange(
    val workerUid: String,
    val workerName: String,
    val fromStatus: String? = null,
    val toStatus: String,
    val changedByName: String? = null,
    val reason: String? = null,
    val changedAtMillis: Long,
)

@Serializable
internal data class NetworkAdminOverview(
    val pendingVerifications: Int,
    val verifiedWorkers: Int,
    val rejectedWorkers: Int,
    val customers: Int,
    val bookingsToday: Int,
    val liveJobs: Int,
    val completedThisWeek: Int,
    val averageRating: Double? = null,
    val recentDecisions: List<NetworkStatusChange> = emptyList(),
    val collectedOnlinePaise: Long = 0,
    val collectedCashPaise: Long = 0,
    val awaitingPayment: Int = 0,
)

@Serializable
internal data class NetworkVerificationRequest(
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val documentType: String,
    val submittedAtMillis: Long,
    val locality: String? = null,
    val categoryIds: List<String> = emptyList(),
    val isResubmission: Boolean = false,
)

@Serializable
internal data class NetworkAdminWorker(
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val status: String,
    val locality: String? = null,
    val categoryIds: List<String> = emptyList(),
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val jobsCompleted: Int = 0,
    val isAvailable: Boolean = false,
    val joinedAtMillis: Long,
)

@Serializable
internal data class NetworkAdminWorkerDetail(
    val summary: NetworkAdminWorker,
    val about: String? = null,
    val yearsExperience: Int? = null,
    val languages: List<String> = emptyList(),
    val services: List<com.sahyog.core.data.catalog.NetworkWorkerService> = emptyList(),
    val documentType: String? = null,
    val documentSubmittedAtMillis: Long? = null,
    val history: List<NetworkStatusChange> = emptyList(),
)

/** The signed-in person's profile as the server returns it. */
@Serializable
internal data class NetworkUser(
    val uid: String,
    val phoneNumber: String,
    val name: String,
    val roles: Set<String>,
    val workerStatus: String? = null,
    val isAdmin: Boolean = false,
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
