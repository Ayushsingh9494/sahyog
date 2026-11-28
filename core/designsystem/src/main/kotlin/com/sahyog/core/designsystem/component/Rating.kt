package com.sahyog.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.R
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import java.util.Locale

private const val MAX_STARS = 5

/** Read-only star rating with fractional fill (4.5 shows four and a half stars). */
@Composable
fun SahyogRatingBar(
    rating: Float,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
) {
    val description = stringResource(R.string.designsystem_rating_description, formatRating(rating))
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        repeat(MAX_STARS) { index ->
            val fill = (rating - index).coerceIn(0f, 1f)
            Box {
                Icon(
                    SahyogIcons.Star,
                    contentDescription = null,
                    tint = SahyogTheme.extendedColors.ratingTrack,
                    modifier = Modifier.size(starSize),
                )
                if (fill > 0f) {
                    Icon(
                        SahyogIcons.Star,
                        contentDescription = null,
                        tint = SahyogTheme.extendedColors.rating,
                        modifier = Modifier
                            .size(starSize)
                            .drawWithContent {
                                // Clip from the start edge so partial stars also read correctly in RTL.
                                val filled = size.width * fill
                                if (layoutDirection == LayoutDirection.Ltr) {
                                    clipRect(right = filled) { this@drawWithContent.drawContent() }
                                } else {
                                    clipRect(left = size.width - filled) { this@drawWithContent.drawContent() }
                                }
                            },
                    )
                }
            }
        }
    }
}

/**
 * Tappable 1–5 star picker. Each star is a 48dp target announced as "3 stars"
 * (selected or not), so it works with TalkBack and for less precise taps.
 *
 * @param rating 0 when nothing is picked yet.
 */
@Composable
fun SahyogRatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier = modifier.selectableGroup()) {
        for (stars in 1..MAX_STARS) {
            val label = pluralStringResource(R.plurals.designsystem_rating_input_star, stars, stars)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = rating == stars,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onRatingChange(stars) },
                    )
                    .semantics { contentDescription = label },
            ) {
                // Lit stars pop in one after another, in order.
                val lit = stars <= rating
                val scale by animateFloatAsState(
                    targetValue = if (lit) 1f else 0.86f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
                    label = "starScale",
                )
                val tint by animateColorAsState(
                    if (lit) SahyogTheme.extendedColors.rating else SahyogTheme.extendedColors.ratingTrack,
                    animationSpec = tween(durationMillis = SahyogMotion.SHORT, delayMillis = if (lit) (stars - 1) * 40 else 0),
                    label = "starTint",
                )
                Icon(
                    SahyogIcons.Star,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}

/** Compact "★ 4.8 (126)" summary for list items and cards. */
@Composable
fun SahyogRatingSummary(
    rating: Float,
    reviewCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(
        R.plurals.designsystem_rating_summary_description,
        reviewCount,
        formatRating(rating),
        reviewCount,
    )
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SahyogIcons.Star,
            contentDescription = null,
            tint = SahyogTheme.extendedColors.rating,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(SahyogSpacing.xs))
        Text(text = formatRating(rating), style = SahyogTheme.typography.labelLarge)
        Spacer(Modifier.width(SahyogSpacing.xs))
        Text(
            text = "($reviewCount)",
            style = SahyogTheme.typography.bodySmall,
            color = SahyogTheme.colors.onSurfaceVariant,
        )
    }
}

private fun formatRating(rating: Float): String = String.format(Locale.getDefault(), "%.1f", rating)
