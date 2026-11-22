package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.WorkerProfileRepository
import com.sahyog.core.data.repository.WorkerProfileState
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.WorkerProfile
import com.sahyog.core.model.WorkerProfileInput
import com.sahyog.core.model.WorkerService
import com.sahyog.core.model.WorkerVerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory worker profile. `refresh` publishes [serverProfile], like a fetch would. */
class TestWorkerProfileRepository : WorkerProfileRepository {
    private val state = MutableStateFlow<WorkerProfileState>(WorkerProfileState.NotLoaded)
    override val profile: StateFlow<WorkerProfileState> = state

    /** What the "server" holds; null means not set up. */
    var serverProfile: WorkerProfile? = null
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var saveResult: AppResult<WorkerProfile>? = null
    var submitResult: AppResult<WorkerProfile>? = null

    var refreshCount = 0
        private set
    val savedInputs = mutableListOf<WorkerProfileInput>()
    val submittedDocuments = mutableListOf<Pair<IdDocumentType, String>>()
    val availabilityChanges = mutableListOf<Boolean>()

    override suspend fun refresh(): AppResult<Unit> {
        refreshCount++
        if (refreshResult is AppResult.Success) {
            state.value = serverProfile?.let { WorkerProfileState.Ready(it) } ?: WorkerProfileState.NotSetUp
        }
        return refreshResult
    }

    override suspend fun saveProfile(input: WorkerProfileInput): AppResult<WorkerProfile> {
        savedInputs += input
        val result = saveResult ?: AppResult.Success(
            (serverProfile ?: TestWorkerProfiles.notSubmitted).copy(
                categoryIds = input.categoryIds,
                services = input.services,
                about = input.about,
                locality = input.locality,
                languages = input.languages,
                yearsExperience = input.yearsExperience,
            ),
        )
        if (result is AppResult.Success) publish(result.data)
        return result
    }

    override suspend fun submitIdDocument(type: IdDocumentType, imageUri: String): AppResult<WorkerProfile> {
        submittedDocuments += type to imageUri
        val current = serverProfile ?: TestWorkerProfiles.notSubmitted
        val result = submitResult ?: AppResult.Success(
            current.copy(verificationStatus = WorkerVerificationStatus.Pending, idDocumentType = type),
        )
        if (result is AppResult.Success) publish(result.data)
        return result
    }

    override suspend fun setAvailability(available: Boolean): AppResult<WorkerProfile> {
        availabilityChanges += available
        val updated = (serverProfile ?: TestWorkerProfiles.verified).copy(isAvailable = available)
        publish(updated)
        return AppResult.Success(updated)
    }

    private fun publish(profile: WorkerProfile) {
        serverProfile = profile
        state.value = WorkerProfileState.Ready(profile)
    }
}

object TestWorkerProfiles {
    val notSubmitted = WorkerProfile(
        categoryIds = listOf("electrician"),
        services = listOf(WorkerService("Fan installation", Paise.rupees(250), PriceUnit.PerVisit)),
        about = "",
        locality = "Model Town",
        languages = listOf("Hindi"),
        yearsExperience = 5,
        isAvailable = true,
        verificationStatus = WorkerVerificationStatus.NotSubmitted,
        idDocumentType = null,
    )
    val pending = notSubmitted.copy(verificationStatus = WorkerVerificationStatus.Pending, idDocumentType = IdDocumentType.Aadhaar)
    val verified = pending.copy(verificationStatus = WorkerVerificationStatus.Verified)
}
