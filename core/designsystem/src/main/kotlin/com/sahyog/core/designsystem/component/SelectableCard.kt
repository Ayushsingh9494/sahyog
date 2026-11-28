package com.sahyog.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/**
 * Large tappable option card with an icon, title and description, e.g. role selection.
 * Behaves as a checkbox for accessibility (several for multi-select); pass
 * [role] = [Role.RadioButton] inside a `selectableGroup()` for a one-of-several choice.
 */
@Composable
fun SahyogSelectableCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
) {
    val colors = SahyogTheme.colors
    val shape = SahyogTheme.shapes.large
    val interactions = remember { MutableInteractionSource() }
    // Selection eases in: background, border and icon colours glide rather than snap.
    val background by animateColorAsState(
        if (selected) colors.primaryContainer.copy(alpha = 0.45f) else SahyogTheme.extendedColors.card,
        label = "selectableBackground",
    )
    val borderColor by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "selectableBorder")
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "selectableBorderWidth")
    val badge by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, label = "selectableBadge")
    val tick by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "selectableTick")
    Row(
        modifier = modifier
            .pressScale(interactions)
            .clip(shape)
            .background(background)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = role,
                interactionSource = interactions,
                indication = ripple(),
                onValueChange = onSelectedChange,
            )
            .padding(SahyogSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(badge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(SahyogSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = SahyogTheme.typography.titleMedium)
            Text(description, style = SahyogTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(SahyogSpacing.sm))
        Icon(
            SahyogIcons.Verified,
            contentDescription = null,
            tint = tick,
            modifier = Modifier.size(24.dp),
        )
    }
}
