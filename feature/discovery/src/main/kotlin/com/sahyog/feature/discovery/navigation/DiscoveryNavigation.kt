package com.sahyog.feature.discovery.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sahyog.feature.discovery.category.CategoryScreen
import com.sahyog.feature.discovery.category.CategoryViewModel
import com.sahyog.feature.discovery.home.HomeScreen
import com.sahyog.feature.discovery.search.SearchScreen
import com.sahyog.feature.discovery.worker.WorkerProfileScreen
import com.sahyog.feature.discovery.worker.WorkerProfileViewModel
import kotlinx.serialization.Serializable

/*
 * Customer discovery destinations. Home and Search are bottom-nav tabs (the app's
 * customer shell uses these routes as tab routes); Category and WorkerProfile are
 * pushed on top of whichever tab opened them.
 */

@Serializable data object HomeRoute

@Serializable data object SearchRoute

@Serializable internal data class CategoryRoute(val categoryId: String)

@Serializable internal data class WorkerProfileRoute(val workerId: String)

fun NavController.navigateToCategory(categoryId: String) = navigate(CategoryRoute(categoryId))

fun NavController.navigateToWorkerProfile(workerId: String) = navigate(WorkerProfileRoute(workerId))

/**
 * Registers all discovery destinations.
 *
 * @param onOpenSearchTab Switches to the Search tab; the shell owns tab switching.
 */
fun NavGraphBuilder.discoveryDestinations(
    navController: NavController,
    onOpenSearchTab: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onSearchClick = onOpenSearchTab,
            onCategoryClick = navController::navigateToCategory,
            onWorkerClick = navController::navigateToWorkerProfile,
        )
    }
    composable<SearchRoute> {
        SearchScreen(
            onCategoryClick = navController::navigateToCategory,
            onWorkerClick = navController::navigateToWorkerProfile,
        )
    }
    composable<CategoryRoute> { entry ->
        val route = entry.toRoute<CategoryRoute>()
        CategoryScreen(
            // Assisted injection keeps the route argument a plain constructor parameter.
            viewModel = hiltViewModel<CategoryViewModel, CategoryViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.categoryId) },
            ),
            onNavigateBack = navController::navigateUp,
            onWorkerClick = navController::navigateToWorkerProfile,
        )
    }
    composable<WorkerProfileRoute> { entry ->
        val route = entry.toRoute<WorkerProfileRoute>()
        WorkerProfileScreen(
            viewModel = hiltViewModel<WorkerProfileViewModel, WorkerProfileViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.workerId) },
            ),
            onNavigateBack = navController::navigateUp,
        )
    }
}
