package com.sahyog.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.sahyog.app.R
import com.sahyog.app.ui.placeholder.CustomerHomePlaceholder
import com.sahyog.app.ui.placeholder.PlaceholderTab
import com.sahyog.app.ui.placeholder.WorkerJobsPlaceholder
import com.sahyog.core.designsystem.catalog.DesignSystemCatalog
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.feature.account.AccountScreen
import kotlinx.serialization.Serializable

// Customer tabs
@Serializable internal data object CustomerHomeRoute
@Serializable internal data object CustomerSearchRoute
@Serializable internal data object CustomerBookingsRoute

// Worker tabs
@Serializable internal data object WorkerJobsRoute
@Serializable internal data object WorkerScheduleRoute
@Serializable internal data object WorkerEarningsRoute

// Shared
@Serializable internal data object AccountRoute
@Serializable internal data object DesignCatalogRoute

private val accountTab = TopLevelTab(
    AccountRoute, R.string.tab_account, SahyogIcons.AccountOutlined, SahyogIcons.Account,
)

@Composable
internal fun CustomerShell(
    user: User,
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    RoleShell(
        tabs = listOf(
            TopLevelTab(CustomerHomeRoute, R.string.tab_home, SahyogIcons.HomeOutlined, SahyogIcons.Home),
            TopLevelTab(CustomerSearchRoute, R.string.tab_search, SahyogIcons.SearchOutlined, SahyogIcons.Search),
            TopLevelTab(
                CustomerBookingsRoute, R.string.tab_bookings, SahyogIcons.BookingsOutlined, SahyogIcons.Bookings,
            ),
            accountTab,
        ),
    ) { navController ->
        composable<CustomerHomeRoute> { CustomerHomePlaceholder(user) }
        composable<CustomerSearchRoute> {
            PlaceholderTab(
                titleRes = R.string.tab_search,
                emptyTitleRes = R.string.placeholder_search_title,
                emptyMessageRes = R.string.placeholder_search_message,
                icon = SahyogIcons.Search,
            )
        }
        composable<CustomerBookingsRoute> {
            PlaceholderTab(
                titleRes = R.string.tab_bookings,
                emptyTitleRes = R.string.placeholder_bookings_title,
                emptyMessageRes = R.string.placeholder_bookings_message,
                icon = SahyogIcons.Bookings,
            )
        }
        sharedDestinations(navController, darkTheme, onThemeChange, showDeveloperOptions)
    }
}

@Composable
internal fun WorkerShell(
    user: User,
    darkTheme: Boolean,
    onThemeChange: (ThemePreference) -> Unit,
    showDeveloperOptions: Boolean,
) {
    RoleShell(
        tabs = listOf(
            TopLevelTab(WorkerJobsRoute, R.string.tab_jobs, SahyogIcons.Jobs, SahyogIcons.Jobs),
            TopLevelTab(
                WorkerScheduleRoute, R.string.tab_schedule, SahyogIcons.BookingsOutlined, SahyogIcons.Bookings,
            ),
            TopLevelTab(WorkerEarningsRoute, R.string.tab_earnings, SahyogIcons.Star, SahyogIcons.Star),
            accountTab,
        ),
    ) { navController ->
        composable<WorkerJobsRoute> { WorkerJobsPlaceholder(user) }
        composable<WorkerScheduleRoute> {
            PlaceholderTab(
                titleRes = R.string.tab_schedule,
                emptyTitleRes = R.string.placeholder_schedule_title,
                emptyMessageRes = R.string.placeholder_schedule_message,
                icon = SahyogIcons.Bookings,
            )
        }
        composable<WorkerEarningsRoute> {
            PlaceholderTab(
                titleRes = R.string.tab_earnings,
                emptyTitleRes = R.string.placeholder_earnings_title,
                emptyMessageRes = R.string.placeholder_earnings_message,
                icon = SahyogIcons.Star,
            )
        }
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
