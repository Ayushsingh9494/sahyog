package com.sahyog.feature.discovery.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.model.Worker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface WorkerProfileUiState {
    data object Loading : WorkerProfileUiState

    /** The worker left the catalog (e.g. no longer active in the cooperative). */
    data object NotFound : WorkerProfileUiState

    data class Ready(val worker: Worker) : WorkerProfileUiState
}

@HiltViewModel(assistedFactory = WorkerProfileViewModel.Factory::class)
class WorkerProfileViewModel @AssistedInject constructor(
    @Assisted workerId: String,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(workerId: String): WorkerProfileViewModel
    }

    val uiState: StateFlow<WorkerProfileUiState> = catalogRepository.worker(workerId)
        .map { worker -> worker?.let(WorkerProfileUiState::Ready) ?: WorkerProfileUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerProfileUiState.Loading)
}
