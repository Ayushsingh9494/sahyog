package com.sahyog.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyog.core.designsystem.R
import com.sahyog.core.designsystem.theme.SahyogBrand
import com.sahyog.core.designsystem.theme.SahyogTheme

/*
 * The Sahyog mark: two interlocking rings, one customer and one worker, joined
 * by the cooperative. Geometry is defined on a 52 x 34 unit grid and is kept
 * identical to app/src/main/res/drawable/ic_launcher_foreground.xml.
 */
private const val GRID_W = 52f
private const val GRID_H = 34f
private const val RING_RADIUS = 13f
private const val RING_STROKE = 7f
private val LeftCenter = Offset(17f, 17f)
private val RightCenter = Offset(35f, 17f)

// The accent ring passes OVER the primary ring at the top crossing and UNDER it at
// the bottom, which creates the interlocked look. The whole top-left quarter is
// re-drawn because the bands cross obliquely: a narrower arc leaves a visible notch.
private const val OVERLAP_START_DEG = 180f
private const val OVERLAP_SWEEP_DEG = 90f

@Composable
fun SahyogLogoMark(
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    primaryRing: Color = SahyogTheme.colors.primary,
    accentRing: Color = SahyogBrand.Marigold,
) {
    val description = stringResource(R.string.designsystem_logo_description)
    Canvas(
        modifier = modifier
            .height(height)
            .width(height * (GRID_W / GRID_H))
            .semantics {
                contentDescription = description
                role = Role.Image
            },
    ) {
        val unit = size.height / GRID_H
        val stroke = Stroke(width = RING_STROKE * unit, cap = StrokeCap.Butt)
        val radius = RING_RADIUS * unit

        drawCircle(accentRing, radius, RightCenter * unit, style = stroke)
        drawCircle(primaryRing, radius, LeftCenter * unit, style = stroke)
        drawArc(
            color = accentRing,
            startAngle = OVERLAP_START_DEG,
            sweepAngle = OVERLAP_SWEEP_DEG,
            useCenter = false,
            topLeft = (RightCenter - Offset(RING_RADIUS, RING_RADIUS)) * unit,
            size = Size(radius * 2, radius * 2),
            style = stroke,
        )
    }
}

/** Mark + "Sahyog" wordmark, for splash, auth and about screens. */
@Composable
fun SahyogLogo(
    modifier: Modifier = Modifier,
    markHeight: Dp = 34.dp,
) {
    val appName = stringResource(R.string.designsystem_brand_name)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = appName },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SahyogLogoMark(height = markHeight)
        Spacer(Modifier.width(markHeight * 0.4f))
        Text(
            text = appName,
            style = SahyogTheme.typography.headlineMedium.copy(
                fontSize = (markHeight.value * 0.9f).sp,
                letterSpacing = (-0.5).sp,
            ),
            color = SahyogTheme.colors.onSurface,
        )
    }
}
