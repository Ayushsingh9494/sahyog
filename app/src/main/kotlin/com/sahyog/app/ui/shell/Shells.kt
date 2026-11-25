package com.sahyog.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.sahyog.app.R
import com.sahyog.core.designsystem.catalog.DesignSystemCatalog
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.model.ThemePreference
import com.sahyog.feature.account.AccountScreen
import com.sahyog.feature.admin.navigation.AdminOverviewRoute
import com.sahyog.feature.admin.navigation.AdminQueueRoute
import com.sahyog.feature.admin.navigation.AdminWorkersRoute
import com.sahyog.feature.admin.navigation.adminDestinations
import com.sahyog.feature.booking.navigation.BookingsRoute
import com.sahyog.feature.booking.navigation.bookingDestinations
import com.sahyog.feature.booking.navigation.navigateToBookService
import com.sahyog.feature.discovery.navigation.HomeRoute
import com.sahyog.feature.discovery.navigation.SearchRoute
import com.sahyog.feature.discovery.navigation.discoveryDestinations
import com.sahyog.feature.discovery.navigation.navigateToWorkerProfile
import com.sahyog.feature.worker.navigation.WorkerEarningsRoute
import com.sahyog.feature.worker.navigation.WorkerHomeRoute
import com.sahyog.feature.worker.navigation.WorkerScheduleRoute
import com.sahyog.feature.worker.navigation.workerDestinations
import kotlinx.serialization.Serializable

// Customer tabs come from the features: Home/Search (discovery) and Bookings (booking).

// Worker tabs come from feature:worker.

// Shared
@Serializable internal data object AccountRoute
@Serializable internal data object DesignCatalogRoute

private val accountTab = TopLevelTab(
    AccountRoute, R.string.tab_account, SahyogIcons.AccountOutlined, SahyogIcons.Account,
)

@Composable
internal fun CustomerShell(
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    RoleShell(
        tabs = listOf(
            TopLevelTab(HomeRoute, R.string.tab_home, SahyogIcons.HomeOutlined, SahyogIcons.Home),
            TopLevelTab(SearchRoute, R.string.tab_search, SahyogIcons.SearchOutlined, SahyogIcons.Search),
            TopLevelTab(BookingsRoute, R.string.tab_bookings, SahyogIcons.BookingsOutlined, SahyogIcons.Bookings),
            accountTab,
        ),
    ) { navController ->
        discoveryDestinations(
            navController = navController,
            onOpenSearchTab = { navController.navigateToTab(SearchRoute) },
            onBookWorker = navController::navigateToBookService,
        )
        bookingDestinations(
            navController = navController,
            onWorkerClick = navController::navigateToWorkerProfile,
            onFindService = { navController.navigateToTab(HomeRoute) },
        )
        sharedDestinations(navController, darkTheme, onThemeChange, showDeveloperOptions)
    }
}

@Composable
internal fun WorkerShell(
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    RoleShell(
        tabs = listOf(
            TopLevelTab(WorkerHomeRoute, R.string.tab_jobs, SahyogIcons.Jobs, SahyogIcons.Jobs),
            TopLevelTab(
                WorkerScheduleRoute, R.string.tab_schedule, SahyogIcons.BookingsOutlined, SahyogIcons.Bookings,
            ),
            TopLevelTab(WorkerEarningsRoute, R.string.tab_earnings, SahyogIcons.Star, SahyogIcons.Star),
            accountTab,
        ),
    ) { navController ->
        workerDestinations(navController)
        sharedDestinations(navController, darkTheme, onThemeChange, showDeveloperOptions)
    }
}

/** Cooperative staff: overview, verification queue, workers directory. */
@Composable
internal fun AdminShell(
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    RoleShell(
        tabs = listOf(
            TopLevelTab(AdminOverviewRoute, R.string.tab_overview, SahyogIcons.HomeOutlined, SahyogIcons.Home),
            TopLevelTab(AdminQueueRoute, R.string.tab_verify, SahyogIcons.VerifiedOutlined, SahyogIcons.Verified),
            TopLevelTab(AdminWorkersRoute, R.string.tab_workers, SahyogIcons.Jobs, SahyogIcons.Jobs),
            accountTab,
        ),
    ) { navController ->
        adminDestinations(navController, onOpenQueueTab = { navController.navigateToTab(AdminQueueRoute) })
        sharedDestinations(navController, darkTheme, onThemeChange, showDeveloperOptions)
    }
}

private fun NavGraphBuilder.sharedDestinations(
    navController: NavHostController,
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    composable<AccountRoute> {
        AccountScreen(
            onOpenDesignCatalog = if (showDeveloperOptions) {
                { navController.navigate(DesignCatalogRoute) }
            } else {
                null
            },
        )
    }
    if (showDeveloperOptions) {
        composable<DesignCatalogRoute> {
            DesignSystemCatalog(
                darkTheme = darkTheme,
                onDarkThemeChange = { dark -> onThemeChange(if (dark) ThemePreference.Dark else ThemePreference.Light) },
                onNavigateBack = navController::navigateUp,
            )
        }
    }
}
