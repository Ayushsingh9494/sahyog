package com.sahyog.feature.worker.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.data.repository.LocationRepository
import com.sahyog.core.data.repository.WorkerProfileRepository
import com.sahyog.core.data.repository.WorkerProfileState
import com.sahyog.core.location.DeviceLocationResult
import com.sahyog.core.model.GeoPoint
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.WorkerProfile
import com.sahyog.core.model.WorkerProfileInput
import com.sahyog.core.model.WorkerService
import com.sahyog.core.ui.location.LocationPermissionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One editable service row. Price is kept as typed text until it's parsed. */
data class ServiceDraft(
    val name: String = "",
    val priceRupees: String = "",
    val unit: PriceUnit = PriceUnit.PerVisit,
) {
    val price: Long? get() = priceRupees.toLongOrNull()

    val isValid: Boolean
        get() = name.trim().length in WorkerProfileInput.SERVICE_NAME_LENGTH &&
            price?.let { it in WorkerProfileInput.SERVICE_PRICE_RUPEES } == true
}

data class WorkerProfileFormUiState(
    val isLoading: Boolean = true,
    /** True when editing an existing profile (vs first-time setup). */
    val isEditing: Boolean = false,
    val categories: List<ServiceCategory> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val services: List<ServiceDraft> = listOf(ServiceDraft()),
    val about: String = "",
    val locality: String = "",
    val languages: Set<String> = emptySet(),
    val yearsExperience: String = "",
    /** Where they work from (approximate); optional, but customers then see distance. */
    val location: GeoPoint? = null,
    val locationProblem: WorkLocationProblem? = null,
    val isLocating: Boolean = false,
    /** Field errors only show after the first save attempt. */
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val categoriesValid get() = selectedCategoryIds.size in WorkerProfileInput.CATEGORY_COUNT
    val servicesValid get() = services.isNotEmpty() && services.all { it.isValid } &&
        services.map { it.name.trim().lowercase() }.distinct().size == services.size
    val localityValid get() = locality.trim().length in WorkerProfileInput.LOCALITY_LENGTH
    val languagesValid get() = languages.size in WorkerProfileInput.LANGUAGE_COUNT
    val yearsValid get() = yearsExperience.toIntOrNull()?.let { it in WorkerProfileInput.YEARS_EXPERIENCE } == true
    val canAddService get() = services.size < WorkerProfileInput.SERVICE_COUNT.last
    val isValid get() = categoriesValid && servicesValid && localityValid && languagesValid && yearsValid
}

/** Why the work location couldn't be set. */
enum class WorkLocationProblem { Denied, PermanentlyDenied, Off, Unavailable }

sealed interface WorkerProfileFormEvent {
    /** @property needsIdDocument The worker still has to submit an ID for verification. */
    data class Saved(val needsIdDocument: Boolean) : WorkerProfileFormEvent

    data class Failed(val error: AppError) : WorkerProfileFormEvent
}

/** Creates (first-time setup) or edits the signed-in worker's profile. */
@HiltViewModel
class WorkerProfileFormViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    private val workerProfileRepository: WorkerProfileRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val form = MutableStateFlow(WorkerProfileFormUiState())

    private val events = Channel<WorkerProfileFormEvent>(Channel.BUFFERED)
    val eventFlow: Flow<WorkerProfileFormEvent> = events.receiveAsFlow()

    val uiState: StateFlow<WorkerProfileFormUiState> = combine(form, catalogRepository.categories) { form, categories ->
        form.copy(categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerProfileFormUiState())

    init {
        viewModelScope.launch {
            // The catalog provides the category list; make sure it's there on a fresh install.
            if (catalogRepository.categories.first().isEmpty()) catalogRepository.refresh()
        }
        viewModelScope.launch {
            if (workerProfileRepository.profile.value == WorkerProfileState.NotLoaded) workerProfileRepository.refresh()
            val existing = (workerProfileRepository.profile.value as? WorkerProfileState.Ready)?.profile
            form.update { if (existing == null) it.copy(isLoading = false) else it.prefilledFrom(existing) }
        }
    }

    fun onCategoryToggle(id: String) = form.update {
        val selected = if (id in it.selectedCategoryIds) it.selectedCategoryIds - id else it.selectedCategoryIds + id
        it.copy(selectedCategoryIds = selected)
    }

    fun onServiceChange(index: Int, service: ServiceDraft) = form.update {
        it.copy(
            services = it.services.mapIndexed { i, s ->
                if (i == index) service.copy(priceRupees = service.priceRupees.filter(Char::isDigit).take(6)) else s
            },
        )
    }

    fun onAddService() = form.update { if (it.canAddService) it.copy(services = it.services + ServiceDraft()) else it }

    fun onRemoveService(index: Int) = form.update {
        if (it.services.size <= 1) it else it.copy(services = it.services.filterIndexed { i, _ -> i != index })
    }

    fun onAboutChange(value: String) = form.update { it.copy(about = value.take(WorkerProfileInput.ABOUT_MAX_LENGTH)) }

    fun onLocalityChange(value: String) =
        form.update { it.copy(locality = value.take(WorkerProfileInput.LOCALITY_LENGTH.last)) }

    fun onLanguageToggle(language: String) = form.update {
        it.copy(languages = if (language in it.languages) it.languages - language else it.languages + language)
    }

    fun onYearsChange(value: String) = form.update { it.copy(yearsExperience = value.filter(Char::isDigit).take(2)) }

    /** The worker answered the permission dialog after tapping "Use my current location". */
    fun onLocationPermissionResult(result: LocationPermissionResult) {
        when (result) {
            LocationPermissionResult.Granted -> locate()
            LocationPermissionResult.Denied -> form.update { it.copy(locationProblem = WorkLocationProblem.Denied) }
            LocationPermissionResult.PermanentlyDenied ->
                form.update { it.copy(locationProblem = WorkLocationProblem.PermanentlyDenied) }
        }
    }

    fun onClearLocation() = form.update { it.copy(location = null, locationProblem = null) }

    private fun locate() {
        if (form.value.isLocating) return
        form.update { it.copy(isLocating = true, locationProblem = null) }
        viewModelScope.launch {
            val result = locationRepository.updateFromDevice()
            form.update {
                when (result) {
                    // Coarsened here too, so the form never holds a precise home location.
                    is DeviceLocationResult.Found -> it.copy(isLocating = false, location = result.point.coarsened())
                    DeviceLocationResult.PermissionDenied ->
                        it.copy(isLocating = false, locationProblem = WorkLocationProblem.Denied)
                    DeviceLocationResult.LocationOff -> it.copy(isLocating = false, locationProblem = WorkLocationProblem.Off)
                    DeviceLocationResult.Unavailable ->
                        it.copy(isLocating = false, locationProblem = WorkLocationProblem.Unavailable)
                }
            }
        }
    }

    fun onSave() {
        val current = form.value
        if (current.isSaving) return
        if (!current.isValid) {
            form.update { it.copy(showErrors = true) }
            return
        }
        form.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val result = workerProfileRepository.saveProfile(
                WorkerProfileInput(
                    categoryIds = current.selectedCategoryIds.toList(),
                    services = current.services.map {
                        WorkerService(it.name.trim(), Paise.rupees(it.price ?: 0), it.unit)
                    },
                    about = current.about.trim(),
                    locality = current.locality.trim(),
                    // Keep the form's canonical order rather than tap order.
                    languages = WorkerProfileInput.LANGUAGES.filter { it in current.languages },
                    yearsExperience = current.yearsExperience.toInt(),
                    location = current.location,
                ),
            )
            form.update { it.copy(isSaving = false) }
            events.send(
                when (result) {
                    is AppResult.Success -> WorkerProfileFormEvent.Saved(needsIdDocument = result.data.idDocumentType == null)
                    is AppResult.Failure -> WorkerProfileFormEvent.Failed(result.error)
                },
            )
        }
    }

    private fun WorkerProfileFormUiState.prefilledFrom(profile: WorkerProfile) = copy(
        isLoading = false,
        isEditing = true,
        selectedCategoryIds = profile.categoryIds.toSet(),
        services = profile.services.map {
            ServiceDraft(it.name, (it.startingPrice.value / 100).toString(), it.unit)
        }.ifEmpty { listOf(ServiceDraft()) },
        about = profile.about,
        locality = profile.locality,
        languages = profile.languages.toSet(),
        yearsExperience = profile.yearsExperience.toString(),
        location = profile.location,
    )
}
