package com.sahyog.feature.admin

import app.cash.turbine.test
import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestAdmin
import com.sahyog.core.testing.TestAdminRepository
import com.sahyog.feature.admin.detail.AdminAction
import com.sahyog.feature.admin.detail.AdminWorkerEvent
import com.sahyog.feature.admin.detail.AdminWorkerUiState
import com.sahyog.feature.admin.detail.AdminWorkerViewModel
import com.sahyog.feature.admin.detail.DocumentState
import com.sahyog.feature.admin.overview.AdminOverviewUiState
import com.sahyog.feature.admin.overview.AdminOverviewViewModel
import com.sahyog.feature.admin.queue.QueueUiState
import com.sahyog.feature.admin.queue.VerificationQueueViewModel
import com.sahyog.feature.admin.workers.WorkersDirectoryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

class AdminViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = TestAdminRepository()
    private val clock = Clock.fixed(TestAdmin.now, ZoneOffset.UTC)

    @Test
    fun `overview shows the figures and previews the three longest waiting`() = runTest {
        repository.queueResult = AppResult.Success((1..5).map { TestAdmin.request("w$it") })
        val ready = AdminOverviewViewModel(repository).uiState.first { it is AdminOverviewUiState.Ready }
            as AdminOverviewUiState.Ready
        assertEquals(2, ready.overview.pendingVerifications)
        assertEquals(listOf("w1", "w2", "w3"), ready.nextInQueue.map { it.workerId })
    }

    @Test
    fun `overview failure shows an error, and a failed refresh keeps the old figures`() = runTest {
        repository.overviewResult = AppResult.Failure(AppError.Forbidden)
        val viewModel = AdminOverviewViewModel(repository)
        assertEquals(AdminOverviewUiState.Error(AppError.Forbidden), viewModel.uiState.first { it !is AdminOverviewUiState.Loading })

        repository.overviewResult = AppResult.Success(TestAdmin.overview(pending = 0))
        viewModel.onRefresh()
        viewModel.uiState.first { it is AdminOverviewUiState.Ready }
        repository.overviewResult = AppResult.Failure(AppError.Network)
        viewModel.onRefresh()
        val after = viewModel.uiState.first { it is AdminOverviewUiState.Ready && !it.isRefreshing } as AdminOverviewUiState.Ready
        assertEquals(0, after.overview.pendingVerifications)
    }

    @Test
    fun `queue lists requests and refreshes after a decision`() = runTest {
        repository.queueResult = AppResult.Success(listOf(TestAdmin.request("w1")))
        val viewModel = VerificationQueueViewModel(repository, clock)
        assertEquals(1, (viewModel.uiState.first { it is QueueUiState.Ready } as QueueUiState.Ready).requests.size)
        repository.queueResult = AppResult.Success(emptyList())
        viewModel.onRefresh()
        viewModel.uiState.first { it is QueueUiState.Ready && it.requests.isEmpty() }
    }

    @Test
    fun `directory searches after a pause in typing and filters by status`() = runTest {
        repository.workers["a"] = TestAdmin.worker("a", WorkerVerificationStatus.Verified, name = "Ravi Kumar")
        repository.workers["b"] = TestAdmin.worker("b", WorkerVerificationStatus.Pending, name = "Sunita Devi")
        val viewModel = WorkersDirectoryViewModel(repository)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.workers.isEmpty()) state = awaitItem()
            assertEquals(2, state.workers.size)

            viewModel.onQueryChange("r")
            viewModel.onQueryChange("ra")
            viewModel.onQueryChange("ravi")
            advanceTimeBy(400)
            while (state.workers.size != 1 || state.isLoading) state = awaitItem()
            assertEquals("Ravi Kumar", state.workers.single().name)

            viewModel.onQueryChange("")
            viewModel.onStatusSelect(WorkerVerificationStatus.Pending)
            advanceTimeBy(400)
            while (state.workers.singleOrNull()?.name != "Sunita Devi" || state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        // Typing quickly searched once for the final text, not for every letter.
        assertTrue(repository.workerQueries.none { it.second == "r" || it.second == "ra" })
    }

    @Test
    fun `a pending worker can be approved or rejected, and the document loads`() = runTest {
        repository.workers["w1"] = TestAdmin.worker("w1", WorkerVerificationStatus.Pending)
        val viewModel = AdminWorkerViewModel("w1", repository, clock)
        val ready = viewModel.uiState.first { it is AdminWorkerUiState.Ready && it.document is DocumentState.Loaded }
            as AdminWorkerUiState.Ready
        assertEquals(listOf(AdminAction.Reject, AdminAction.Approve), ready.actions)

        viewModel.eventFlow.test {
            viewModel.onApprove()
            assertEquals(AdminWorkerEvent.Done(AdminAction.Approve, "Worker w1"), awaitItem())
        }
        assertEquals(Triple("approve", "w1", null), repository.decisions.single())
    }

    @Test
    fun `a verified worker can only be revoked, and a conflict reloads the latest status`() = runTest {
        repository.workers["w1"] = TestAdmin.worker("w1", WorkerVerificationStatus.Verified)
        val viewModel = AdminWorkerViewModel("w1", repository, clock)
        val ready = viewModel.uiState.first { it is AdminWorkerUiState.Ready } as AdminWorkerUiState.Ready
        assertEquals(listOf(AdminAction.Revoke), ready.actions)

        // Approve isn't offered for a verified worker, so it does nothing.
        viewModel.onApprove()
        assertTrue(repository.decisions.isEmpty())

        repository.decisionResult = AppResult.Failure(AppError.Validation("status"))
        viewModel.eventFlow.test {
            viewModel.onRevoke("Customer complaints")
            assertEquals(AdminWorkerEvent.Failed(AppError.Validation("status")), awaitItem())
        }
        assertEquals(Triple("revoke", "w1", "Customer complaints"), repository.decisions.single())
    }

    @Test
    fun `a missing document is reported without blocking the review`() = runTest {
        repository.workers["w1"] = TestAdmin.worker("w1", WorkerVerificationStatus.Pending)
        repository.documentResult = AppResult.Failure(AppError.Network)
        val viewModel = AdminWorkerViewModel("w1", repository, clock)
        val ready = viewModel.uiState.first { it is AdminWorkerUiState.Ready && it.document is DocumentState.Failed }
            as AdminWorkerUiState.Ready
        assertEquals(DocumentState.Failed(AppError.Network), ready.document)
        repository.documentResult = AppResult.Success(byteArrayOf(9))
        viewModel.onRetryDocument()
        viewModel.uiState.first { (it as? AdminWorkerUiState.Ready)?.document is DocumentState.Loaded }
    }
}
