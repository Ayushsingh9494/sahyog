package com.sahyog.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sahyog.core.designsystem.theme.SahyogTheme

/**
 * Single-choice selector for 2-4 short options (mode, theme).
 * Uses the brand's primary container for the selected segment, matching chips and
 * the navigation indicator. Material's default (secondary/marigold) would read as a warning.
 */
@Composable
fun <T> SahyogSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = SahyogTheme.colors.primaryContainer,
        activeContentColor = SahyogTheme.colors.onPrimaryContainer,
        activeBorderColor = SahyogTheme.colors.outline,
        inactiveContainerColor = SahyogTheme.extendedColors.card,
        inactiveContentColor = SahyogTheme.colors.onSurface,
        inactiveBorderColor = SahyogTheme.colors.outline,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = colors,
            ) {
                Text(label(option), style = SahyogTheme.typography.labelLarge)
            }
        }
    }
}
