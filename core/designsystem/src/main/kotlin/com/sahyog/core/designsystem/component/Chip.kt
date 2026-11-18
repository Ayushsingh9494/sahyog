package com.sahyog.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sahyog.core.designsystem.theme.SahyogTheme

/** Pill-shaped selectable chip for categories and filters. */
@Composable
fun SahyogFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, style = SahyogTheme.typography.labelLarge) },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SahyogTheme.colors.primaryContainer,
            selectedLabelColor = SahyogTheme.colors.onPrimaryContainer,
            selectedLeadingIconColor = SahyogTheme.colors.onPrimaryContainer,
        ),
    )
}
