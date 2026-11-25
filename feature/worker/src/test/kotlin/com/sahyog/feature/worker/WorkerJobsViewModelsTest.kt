package com.sahyog.feature.worker

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.booking.BookingErrorFields
import com.sahyog.core.data.worker.WorkerErrorFields
import com.sahyog.core.model.BookingReview
import com.sahyog.core.model.BookingStatus
import com.sahyog.core.model.JobAction
import com.sahyog.core.model.Paise
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestJobRepository
import com.sahyog.core.testing.TestJobs
import com.sahyog.core.testing.TestWorkerProfileRepository
import com.sahyog.core.testing.TestWorkerProfiles
import com.sahyog.feature.worker.home.WorkerHomeUiState
import com.sahyog.feature.worker.home.WorkerHomeViewModel
import com.sahyog.feature.worker.job.JobDetailEvent
import com.sahyog.feature.worker.job.JobDetailUiState
import com.sahyog.feature.worker.job.JobDetailViewModel
import com.sahyog.feature.worker.schedule.EarningsViewModel
import com.sahyog.feature.worker.schedule.ScheduleViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WorkerJobsViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(TestJobs.date.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val jobs = TestJobRepository()
    private val workers = TestWorkerProfileRepository().apply { serverProfile = TestWorkerProfiles.verified }

    // --- Home ---

    @Test
    fun `verified home splits jobs into requests, active and upcoming`() = runTest {
        jobs.setJobs(
            listOf(
                TestJobs.job("new"),
                TestJobs.job("going", BookingStatus.WorkerEnRoute),
                TestJobs.job("later", BookingStatus.Accepted),
                TestJobs.job("old", BookingStatus.Completed),
            ),
        )
        WorkerHomeViewModel(workers, jobs, clock).uiState.test {
            val ready = awaitReady()
            assertEquals(listOf("new"), ready.requests.map { it.id })
            assertEquals(listOf("going"), ready.active.map { it.id })
            assertEquals(listOf("later"), ready.upcoming.map { it.id })
        }
        assertEquals(1, jobs.refreshCount)
    }

    @Test
    fun `home shows the average of customers' ratings, none before the first`() = runTest {
        WorkerHomeViewModel(workers, jobs, clock).uiState.test {
            assertNull(awaitReady().rating)
            jobs.setJobs(
                listOf(
                    TestJobs.job("a", BookingStatus.Completed).copy(review = BookingReview(5, null, Instant.EPOCH)),
                    TestJobs.job("b", BookingStatus.Completed).copy(review = BookingReview(4, "Good", Instant.EPOCH)),
                    TestJobs.job("c", BookingStatus.Completed),
                ),
            )
            val rated = awaitReady { it.ratingCount == 2 }
            assertEquals(4.5f, rated.rating)
        }
    }

    @Test
    fun `jobs are not fetched before verification`() = runTest {
        workers.serverProfile = TestWorkerProfiles.pending
        WorkerHomeViewModel(workers, jobs, clock).uiState.test { awaitReady() }
        assertEquals(0, jobs.refreshCount)
    }

    @Test
    fun `accepting from the home tab moves the request to upcoming`() = runTest {
        jobs.setJobs(listOf(TestJobs.job("new")))
        val viewModel = WorkerHomeViewModel(workers, jobs, clock)
        viewModel.uiState.test {
            awaitReady { it.requests.isNotEmpty() }
            viewModel.onAccept("new")
            val ready = awaitReady { it.upcoming.isNotEmpty() && it.busyJobIds.isEmpty() }
            assertTrue(ready.requests.isEmpty())
        }
    }

    // --- Job detail ---

    @Test
    fun `full lifecycle from the detail screen`() = runTest {
        jobs.setJobs(listOf(TestJobs.job("j1")))
        val viewModel = JobDetailViewModel("j1", jobs, clock)
        viewModel.eventFlow.test {
            viewModel.onAccept()
            assertEquals(JobDetailEvent.Done(JobAction.Accept), awaitItem())
            viewModel.onStartTrip()
            assertEquals(JobDetailEvent.Done(JobAction.StartTrip), awaitItem())
            viewModel.onStartJob("0000")
            assertEquals(
                JobDetailEvent.Failed(JobAction.StartJob, AppError.Validation(WorkerErrorFields.START_CODE)),
                awaitItem(),
            )
            viewModel.onStartJob(TestJobs.START_CODE)
            assertEquals(JobDetailEvent.Done(JobAction.StartJob), awaitItem())
            viewModel.onComplete()
            assertEquals(JobDetailEvent.Done(JobAction.Complete), awaitItem())
        }
        viewModel.uiState.test {
            var item = awaitItem()
            while (item !is JobDetailUiState.Ready) item = awaitItem()
            assertEquals(BookingStatus.Completed, item.job.status)
            assertTrue(item.actions.isEmpty())
        }
    }

    @Test
    fun `action refused because the customer cancelled is reported and re-fetched`() = runTest {
        jobs.setJobs(listOf(TestJobs.job("j1", BookingStatus.Cancelled)))
        val viewModel = JobDetailViewModel("j1", jobs, clock)
        viewModel.eventFlow.test {
            viewModel.onAccept()
            assertEquals(
                JobDetailEvent.Failed(JobAction.Accept, AppError.Validation(BookingErrorFields.STATUS)),
                awaitItem(),
            )
        }
        // Once on open, once after the refusal.
        assertEquals(2, jobs.refreshCount)
    }

    // --- Schedule and earnings ---

    @Test
    fun `schedule groups upcoming jobs by day and skips past or closed ones`() = runTest {
        val today = TestJobs.date
        jobs.setJobs(
            listOf(
                TestJobs.job("tomorrow", BookingStatus.Accepted, TimeSlot(today.plusDays(1), 9)),
                TestJobs.job("today-late", BookingStatus.Accepted, TimeSlot(today, 17)),
                TestJobs.job("today-early", BookingStatus.InProgress, TimeSlot(today, 9)),
                TestJobs.job("yesterday", BookingStatus.Accepted, TimeSlot(today.minusDays(1), 9)),
                TestJobs.job("request", BookingStatus.Requested, TimeSlot(today, 11)),
            ),
        )
        ScheduleViewModel(jobs, clock).uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(listOf(today, today.plusDays(1)), state.days.map { it.first })
            assertEquals(listOf("today-early", "today-late"), state.days.first().second.map { it.id })
        }
    }

    @Test
    fun `earnings total completed jobs, newest first`() = runTest {
        val now = clock.instant()
        jobs.setJobs(
            listOf(
                TestJobs.job("a", BookingStatus.Completed, rupees = 300, completedAt = now.minusSeconds(3_600)),
                TestJobs.job("b", BookingStatus.Completed, rupees = 500, completedAt = now),
                TestJobs.job("c", BookingStatus.InProgress, rupees = 900),
            ),
        )
        EarningsViewModel(jobs, clock).uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(Paise.rupees(800), state.summary?.thisWeek)
            assertEquals(listOf("b", "a"), state.completed.map { it.id })
        }
    }

    private suspend fun ReceiveTurbine<WorkerHomeUiState>.awaitReady(
        predicate: (WorkerHomeUiState.Ready) -> Boolean = { true },
    ): WorkerHomeUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is WorkerHomeUiState.Ready && predicate(item)) return item
        }
    }
}
