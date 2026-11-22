package com.sahyog.feature.worker.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahyog.core.data.repository.JobRepository
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.EarningsSummary
import com.sahyog.core.model.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    /** Accepted and ongoing jobs from today on, grouped by day, soonest first. */
    val days: List<Pair<LocalDate, List<Job>>> = emptyList(),
)

/** Worker's Schedule tab: what's coming up, day by day. */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ScheduleUiState> = jobRepository.jobs.map { jobs ->
        val today = LocalDate.now(clock)
        ScheduleUiState(
            isLoading = false,
            today = today,
            days = jobs
                .filter { it.status in SCHEDULED && !it.slot.date.isBefore(today) }
                .groupBy { it.slot.date }
                .toSortedMap()
                .map { (day, dayJobs) -> day to dayJobs.sortedBy { it.slot.startHour } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState(today = LocalDate.now(clock)))

    init {
        viewModelScope.launch { jobRepository.refresh() }
    }

    private companion object {
        val SCHEDULED = setOf(BookingStatus.Accepted, BookingStatus.WorkerEnRoute, BookingStatus.InProgress)
    }
}

data class EarningsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val summary: EarningsSummary? = null,
    /** Completed jobs, most recent first. */
    val completed: List<Job> = emptyList(),
)

/** Worker's Earnings tab: totals of completed jobs and the list behind them. */
@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<EarningsUiState> = jobRepository.jobs.map { jobs ->
        EarningsUiState(
            isLoading = false,
            today = LocalDate.now(clock),
            summary = EarningsSummary.of(jobs, LocalDate.now(clock), clock.zone),
            completed = jobs
                .filter { it.status == BookingStatus.Completed }
                .sortedByDescending { it.completedAt ?: it.createdAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EarningsUiState(today = LocalDate.now(clock)))

    init {
        viewModelScope.launch { jobRepository.refresh() }
    }
}
