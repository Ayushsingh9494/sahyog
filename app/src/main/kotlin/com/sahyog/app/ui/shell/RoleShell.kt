package com.sahyog.app.ui.shell

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sahyog.core.designsystem.component.SahyogNavigationBar
import com.sahyog.core.designsystem.component.SahyogNavigationBarItem
import com.sahyog.core.designsystem.component.SahyogTransitions
import com.sahyog.core.designsystem.theme.SahyogTheme

/** A bottom-navigation destination. [route] is a @Serializable route object. */
internal data class TopLevelTab(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Scaffold + bottom bar + inner NavHost shared by the Customer and Worker sections.
 * The bottom bar is shown on top-level tabs only and hidden on pushed screens.
 */
@Composable
internal fun RoleShell(
    tabs: List<TopLevelTab>,
    builder: NavGraphBuilder.(NavHostController) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    fun TopLevelTab.isSelected() =
        currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
    val onTopLevel = tabs.any { it.isSelected() }

    Scaffold(
        containerColor = SahyogTheme.colors.surface,
        // Each screen draws its own top bar and handles the top inset itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTopLevel) {
                SahyogNavigationBar {
                    tabs.forEach { tab ->
                        SahyogNavigationBarItem(
                            selected = tab.isSelected(),
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = tab.icon,
                            selectedIcon = tab.selectedIcon,
                            label = stringResource(tab.labelRes),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = tabs.first().route,
            // consumeWindowInsets stops inner screens from adding the nav-bar inset a second time.
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            // Switching tabs cross-fades; going deeper (or back) slides.
            enterTransition = { if (isTabSwitch(tabs)) SahyogTransitions.tabEnter else SahyogTransitions.push },
            exitTransition = { if (isTabSwitch(tabs)) SahyogTransitions.tabExit else SahyogTransitions.pushExit },
            popEnterTransition = { if (isTabSwitch(tabs)) SahyogTransitions.tabEnter else SahyogTransitions.pop },
            popExitTransition = { if (isTabSwitch(tabs)) SahyogTransitions.tabExit else SahyogTransitions.popExit },
        ) {
            builder(navController)
        }
    }
}

/** Both ends of the transition are top-level tabs. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(tabs: List<TopLevelTab>): Boolean {
    fun NavBackStackEntry.isTab() = tabs.any { tab -> destination.hierarchy.any { it.hasRoute(tab.route::class) } }
    return initialState.isTab() && targetState.isTab()
}

/**
 * Standard bottom-nav behaviour: one copy of each tab on the stack, and each tab
 * keeps its own scroll/back-stack state when you switch away and back.
 */
internal fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
