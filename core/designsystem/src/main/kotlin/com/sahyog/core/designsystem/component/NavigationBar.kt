package com.sahyog.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogTheme

/** Bottom navigation container for a role's top-level destinations. */
@Composable
fun SahyogNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = SahyogTheme.colors.surfaceContainer,
        tonalElevation = SahyogElevation.none,
        content = content,
    )
}

/**
 * One bottom-nav destination. Uses a filled icon when selected and an outlined one
 * otherwise. This gives a second cue besides color, which helps color-blind users.
 */
@Composable
fun RowScope.SahyogNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { Text(label, style = SahyogTheme.typography.labelMedium) },
        modifier = modifier,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = SahyogTheme.colors.onPrimaryContainer,
            selectedTextColor = SahyogTheme.colors.onSurface,
            indicatorColor = SahyogTheme.colors.primaryContainer,
            unselectedIconColor = SahyogTheme.colors.onSurfaceVariant,
            unselectedTextColor = SahyogTheme.colors.onSurfaceVariant,
        ),
    )
}
