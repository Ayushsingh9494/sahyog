package com.sahyog.feature.worker.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sahyog.feature.worker.home.WorkerHomeScreen
import com.sahyog.feature.worker.profile.WorkerProfileFormScreen
import com.sahyog.feature.worker.verification.IdVerificationScreen
import kotlinx.serialization.Serializable

/** The worker's main bottom-nav tab (Jobs). */
@Serializable data object WorkerHomeRoute

@Serializable internal data object WorkerProfileFormRoute

@Serializable internal data object IdVerificationRoute

/** Registers the worker destinations inside the worker shell. */
fun NavGraphBuilder.workerDestinations(navController: NavController) {
    composable<WorkerHomeRoute> {
        WorkerHomeScreen(
            onSetUpProfile = { navController.navigate(WorkerProfileFormRoute) },
            onAddId = { navController.navigate(IdVerificationRoute) },
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
