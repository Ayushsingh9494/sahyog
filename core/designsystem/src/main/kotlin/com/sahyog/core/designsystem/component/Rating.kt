package com.sahyog.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
