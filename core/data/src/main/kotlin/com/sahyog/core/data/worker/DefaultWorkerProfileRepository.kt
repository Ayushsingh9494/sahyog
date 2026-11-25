package com.sahyog.core.data.worker

import android.content.Context
import androidx.core.net.toUri
import com.sahyog.core.common.dispatcher.Dispatcher
import com.sahyog.core.common.dispatcher.SahyogDispatcher
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.catalog.NetworkWorkerService
import com.sahyog.core.data.network.ApiException
import com.sahyog.core.data.network.apiCall
import com.sahyog.core.data.repository.WorkerProfileRepository
import com.sahyog.core.data.repository.WorkerProfileState
import com.sahyog.core.data.repository.WorkerStatusListener
import com.sahyog.core.data.session.UserDataCleaner
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.WorkerProfile
import com.sahyog.core.model.WorkerProfileInput
import com.sahyog.core.model.WorkerService
import com.sahyog.core.model.WorkerVerificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in worker's profile. Small and always fetched fresh from the server, so it
 * is kept in memory rather than in Room; cleared on sign-out.
 */
@Singleton
internal class DefaultWorkerProfileRepository @Inject constructor(
    private val network: WorkerNetworkDataSource,
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(SahyogDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    private val statusListener: WorkerStatusListener,
) : WorkerProfileRepository, UserDataCleaner {

    private val state = MutableStateFlow<WorkerProfileState>(WorkerProfileState.NotLoaded)
    override val profile: StateFlow<WorkerProfileState> = state.asStateFlow()

    override suspend fun refresh(): AppResult<Unit> = apiCall {
        val profile = network.fetchMyProfile()
        if (profile == null) state.value = WorkerProfileState.NotSetUp else profile.publish()
    }

    override suspend fun saveProfile(input: WorkerProfileInput): AppResult<WorkerProfile> = apiCall {
        network.saveProfile(
            NetworkWorkerProfileInput(
                categoryIds = input.categoryIds,
                services = input.services.map { NetworkWorkerService(it.name, it.startingPrice.value, it.unit.name) },
                about = input.about,
                locality = input.locality,
                languages = input.languages,
                yearsExperience = input.yearsExperience,
                latitude = input.location?.latitude,
                longitude = input.location?.longitude,
            ),
        ).publish()
    }

    override suspend fun submitIdDocument(type: IdDocumentType, imageUri: String): AppResult<WorkerProfile> = apiCall {
        val (bytes, mimeType) = readImage(imageUri)
        network.submitIdDocument(type.name, bytes, mimeType).publish()
    }

    override suspend fun setAvailability(available: Boolean): AppResult<WorkerProfile> = apiCall {
        network.setAvailability(available).publish()
    }

    override suspend fun clearUserData() {
        state.value = WorkerProfileState.NotLoaded
    }

    /** Also keeps the account's verification badge in step (e.g. right after approval). */
    private suspend fun NetworkWorkerProfile.publish(): WorkerProfile =
        asExternalModel().also {
            state.value = WorkerProfileState.Ready(it)
            statusListener.onWorkerStatus(it.verificationStatus)
        }

    /**
     * Reads a Photo Picker image. Reading stops one byte past the limit, so a huge file
     * is rejected without loading it all into memory.
     */
    private suspend fun readImage(uriString: String): Pair<ByteArray, String> = withContext(ioDispatcher) {
        val uri = uriString.toUri()
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        if (!mimeType.startsWith("image/")) throw ApiException(AppError.Validation(WorkerErrorFields.DOCUMENT))
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readAtMost(IdDocumentType.MAX_IMAGE_BYTES + 1) }
        } catch (e: SecurityException) {
            null // The picker's temporary read grant expired.
        } ?: throw ApiException(AppError.Validation(WorkerErrorFields.DOCUMENT))
        if (bytes.size > IdDocumentType.MAX_IMAGE_BYTES) {
            throw ApiException(AppError.Validation(WorkerErrorFields.DOCUMENT_TOO_LARGE))
        }
        bytes to mimeType
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read == -1) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}

internal fun NetworkWorkerProfile.asExternalModel() = WorkerProfile(
    categoryIds = categoryIds,
    services = services.map { service ->
        WorkerService(
            name = service.name,
            startingPrice = Paise(service.pricePaise),
            unit = PriceUnit.entries.firstOrNull { it.name == service.unit } ?: PriceUnit.PerVisit,
        )
    },
    about = about,
    locality = locality,
    languages = languages,
    yearsExperience = yearsExperience,
    isAvailable = isAvailable,
    verificationStatus = WorkerVerificationStatus.entries.firstOrNull { it.name == status }
        ?: WorkerVerificationStatus.NotSubmitted,
    idDocumentType = IdDocumentType.entries.firstOrNull { it.name == documentType },
    location = latitude?.let { lat -> longitude?.let { lng -> GeoPoint(lat, lng) } },
)
