package com.sahyog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/** Semantic tone of a badge. Maps booking/verification states to colors in one place. */
enum class StatusTone { Neutral, Brand, Info, Success, Warning, Error }

/** Small pill label, e.g. "Verified", "In progress", "Payment pending". */
@Composable
fun SahyogStatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val (container, content) = tone.colors()
    Row(
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = SahyogSpacing.md, vertical = SahyogSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(SahyogSpacing.xs))
        }
        Text(text = text, color = content, style = SahyogTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusTone.colors(): Pair<Color, Color> {
    val c = SahyogTheme.colors
    val x = SahyogTheme.extendedColors
    return when (this) {
        StatusTone.Neutral -> c.surfaceContainerHighest to c.onSurfaceVariant
        StatusTone.Brand -> c.primaryContainer to c.onPrimaryContainer
        StatusTone.Info -> c.tertiaryContainer to c.onTertiaryContainer
        StatusTone.Success -> x.successContainer to x.onSuccessContainer
        StatusTone.Warning -> x.warningContainer to x.onWarningContainer
        StatusTone.Error -> c.errorContainer to c.onErrorContainer
    }
}
