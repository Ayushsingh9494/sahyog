package com.sahyog.feature.worker.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sahyog.feature.worker.home.WorkerHomeScreen
import com.sahyog.feature.worker.job.JobDetailScreen
import com.sahyog.feature.worker.job.JobDetailViewModel
import com.sahyog.feature.worker.profile.WorkerProfileFormScreen
import com.sahyog.feature.worker.schedule.EarningsScreen
import com.sahyog.feature.worker.schedule.ScheduleScreen
import com.sahyog.feature.worker.verification.IdVerificationScreen
import kotlinx.serialization.Serializable

/** The worker's bottom-nav tabs. */
@Serializable data object WorkerHomeRoute

@Serializable data object WorkerScheduleRoute

@Serializable data object WorkerEarningsRoute

@Serializable internal data class JobDetailRoute(val jobId: String)

@Serializable internal data object WorkerProfileFormRoute

@Serializable internal data object IdVerificationRoute

/** Registers the worker destinations inside the worker shell. */
fun NavGraphBuilder.workerDestinations(navController: NavController) {
    composable<WorkerHomeRoute> {
        WorkerHomeScreen(
            onSetUpProfile = { navController.navigate(WorkerProfileFormRoute) },
            onAddId = { navController.navigate(IdVerificationRoute) },
            onJobClick = { navController.navigate(JobDetailRoute(it)) },
        )
    }
    composable<WorkerScheduleRoute> {
        ScheduleScreen(onJobClick = { navController.navigate(JobDetailRoute(it)) })
    }
    composable<WorkerEarningsRoute> {
        EarningsScreen(onJobClick = { navController.navigate(JobDetailRoute(it)) })
    }
    composable<JobDetailRoute> { entry ->
        val route = entry.toRoute<JobDetailRoute>()
        JobDetailScreen(
            viewModel = hiltViewModel<JobDetailViewModel, JobDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.jobId) },
            ),
            onNavigateBack = navController::navigateUp,
        )
    }
    composable<WorkerProfileFormRoute> {
        WorkerProfileFormScreen(
            onNavigateBack = navController::navigateUp,
            onSaved = { needsIdDocument ->
                if (needsIdDocument) {
                    // First-time setup continues straight to ID verification.
                    navController.navigate(IdVerificationRoute) {
                        popUpTo<WorkerProfileFormRoute> { inclusive = true }
                    }
                } else {
                    navController.navigateUp()
                }
            },
        )
    }
    composable<IdVerificationRoute> {
        IdVerificationScreen(
            onNavigateBack = navController::navigateUp,
            onSubmitted = navController::navigateUp,
        )
    }
}
