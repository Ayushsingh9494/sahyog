package com.sahyog.feature.admin.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sahyog.feature.admin.detail.AdminWorkerScreen
import com.sahyog.feature.admin.detail.AdminWorkerViewModel
import com.sahyog.feature.admin.overview.AdminOverviewScreen
import com.sahyog.feature.admin.overview.AdminOverviewViewModel
import com.sahyog.feature.admin.queue.VerificationQueueScreen
import com.sahyog.feature.admin.queue.VerificationQueueViewModel
import com.sahyog.feature.admin.workers.WorkersDirectoryScreen
import com.sahyog.feature.admin.workers.WorkersDirectoryViewModel
import kotlinx.serialization.Serializable

/** Admin bottom-nav tabs. */
@Serializable data object AdminOverviewRoute

@Serializable data object AdminQueueRoute

@Serializable data object AdminWorkersRoute

@Serializable internal data class AdminWorkerRoute(val workerId: String)

/** The confirmation a decision hands back to the screen it was opened from. */
private const val DECISION_MESSAGE_KEY = "admin_decision_message"

/**
 * Registers the admin destinations.
 * @param onOpenQueueTab Switches to the Verify tab (owned by the shell in :app).
 */
fun NavGraphBuilder.adminDestinations(navController: NavController, onOpenQueueTab: () -> Unit) {
    val openWorker: (String) -> Unit = { navController.navigate(AdminWorkerRoute(it)) }

    composable<AdminOverviewRoute> { entry ->
        val viewModel = hiltViewModel<AdminOverviewViewModel>()
        val snackbar = remember { SnackbarHostState() }
        DecisionResult(entry, snackbar, viewModel::onRefresh)
        AdminOverviewScreen(onOpenQueueTab, openWorker, viewModel, snackbar)
    }
    composable<AdminQueueRoute> { entry ->
        val viewModel = hiltViewModel<VerificationQueueViewModel>()
        val snackbar = remember { SnackbarHostState() }
        DecisionResult(entry, snackbar, viewModel::onRefresh)
        VerificationQueueScreen(viewModel, openWorker, snackbar)
    }
    composable<AdminWorkersRoute> { entry ->
        val viewModel = hiltViewModel<WorkersDirectoryViewModel>()
        val snackbar = remember { SnackbarHostState() }
        DecisionResult(entry, snackbar, viewModel::onRefresh)
        WorkersDirectoryScreen(viewModel, openWorker, snackbar)
    }
    composable<AdminWorkerRoute> { entry ->
        val route = entry.toRoute<AdminWorkerRoute>()
        AdminWorkerScreen(
            viewModel = hiltViewModel<AdminWorkerViewModel, AdminWorkerViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.workerId) },
            ),
            onNavigateBack = navController::navigateUp,
            onDecided = { message ->
                navController.previousBackStackEntry?.savedStateHandle?.set(DECISION_MESSAGE_KEY, message)
                navController.navigateUp()
            },
        )
    }
}

/** Shows the decision's confirmation once and refreshes the list it came back to. */
@Composable
private fun DecisionResult(entry: NavBackStackEntry, snackbar: SnackbarHostState, onRefresh: () -> Unit) {
    val message by entry.savedStateHandle.getStateFlow<String?>(DECISION_MESSAGE_KEY, null).collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        onRefresh()
        // Cleared only after showing: clearing first would restart this effect and cancel the snackbar.
        try {
            snackbar.showSnackbar(text)
        } finally {
            entry.savedStateHandle[DECISION_MESSAGE_KEY] = null
        }
    }
}
