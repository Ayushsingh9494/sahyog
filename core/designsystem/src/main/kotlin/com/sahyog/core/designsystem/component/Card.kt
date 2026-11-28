package com.sahyog.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/**
 * Content container. A hairline border instead of a heavy shadow keeps lists of
 * cards calm and premium-looking. Pass [onClick] to make the whole card tappable.
 */
@Composable
fun SahyogCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(SahyogSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = SahyogTheme.extendedColors.card,
        contentColor = SahyogTheme.colors.onSurface,
    )
    val border = BorderStroke(1.dp, SahyogTheme.colors.outlineVariant.copy(alpha = 0.6f))
    val shape = SahyogTheme.shapes.large

    if (onClick != null) {
        // Tappable cards sink a touch while pressed, so taps feel physical.
        val interactions = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier.pressScale(interactions),
            shape = shape,
            colors = colors,
            border = border,
            interactionSource = interactions,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
