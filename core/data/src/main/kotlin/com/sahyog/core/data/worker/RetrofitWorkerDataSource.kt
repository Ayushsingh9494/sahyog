package com.sahyog.core.data.worker

import com.sahyog.core.data.booking.NetworkBooking
import com.sahyog.core.data.network.NetworkAvailability
import com.sahyog.core.data.network.NetworkStartJob
import com.sahyog.core.data.network.SahyogApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import javax.inject.Inject

/** The worker side of the Sahyog API: own profile, ID upload and the job lifecycle. */
internal class RetrofitWorkerDataSource @Inject constructor(
    private val api: SahyogApi,
) : WorkerNetworkDataSource {

    override suspend fun fetchMyProfile(): NetworkWorkerProfile? {
        val response = api.workerProfile()
        return when {
            response.code() == HTTP_NO_CONTENT -> null
            response.isSuccessful -> response.body()
            else -> throw HttpException(response)
        }
    }

    override suspend fun saveProfile(input: NetworkWorkerProfileInput): NetworkWorkerProfile =
        api.saveWorkerProfile(input)

    override suspend fun submitIdDocument(type: String, image: ByteArray, mimeType: String): NetworkWorkerProfile {
        val typePart = type.toRequestBody(TEXT)
        // The server checks the bytes themselves; the file name is just a label.
        val imagePart = MultipartBody.Part.createFormData("image", "id-document", image.toRequestBody(mimeType.toMediaType()))
        return api.submitDocument(typePart, imagePart)
    }

    override suspend fun setAvailability(available: Boolean): NetworkWorkerProfile =
        api.setAvailability(NetworkAvailability(available))

    override suspend fun fetchJobs(): List<NetworkBooking> = api.jobs()

    override suspend fun acceptJob(jobId: String): NetworkBooking = api.jobAction(jobId, "accept")

    override suspend fun declineJob(jobId: String): NetworkBooking = api.jobAction(jobId, "decline")

    override suspend fun startTrip(jobId: String): NetworkBooking = api.jobAction(jobId, "start-trip")

    override suspend fun startJob(jobId: String, startCode: String): NetworkBooking =
        api.startJob(jobId, NetworkStartJob(startCode))

    override suspend fun completeJob(jobId: String): NetworkBooking = api.jobAction(jobId, "complete")

    override suspend fun markCashReceived(jobId: String): NetworkBooking = api.cashReceived(jobId)

    private companion object {
        const val HTTP_NO_CONTENT = 204
        val TEXT = "text/plain".toMediaType()
    }
}
