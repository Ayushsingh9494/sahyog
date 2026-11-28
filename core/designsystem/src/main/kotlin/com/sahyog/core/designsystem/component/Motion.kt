package com.sahyog.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.theme.SahyogTheme

/**
 * Sahyog's motion: short, calm and purposeful. Everything here goes through Compose's
 * animation clock, so the system "Remove animations" setting shortens it to nothing.
 */
object SahyogMotion {
    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 600

    /** Gentle spring for things the finger touches. */
    val pressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * A ring that draws itself, then a tick that strokes in, with a soft pop at the end.
 * Plays once when it first appears (pass [animate] = false to show it finished, e.g. on
 * screens restored later).
 */
@Composable
fun SahyogSuccessCheck(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    color: Color = SahyogTheme.colors.primary,
    animate: Boolean = true,
) {
    val ring = remember { Animatable(if (animate) 0f else 1f) }
    val tick = remember { Animatable(if (animate) 0f else 1f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        ring.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        tick.animateTo(1f, tween(durationMillis = 280, easing = LinearOutSlowInEasing))
        pop.animateTo(1.08f, tween(durationMillis = 110))
        pop.animateTo(1f, SahyogMotion.pressSpring)
    }
    val fill = color.copy(alpha = 0.12f)
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        val stroke = this.size.minDimension * 0.075f
        val radius = this.size.minDimension / 2 - stroke
        drawCircle(fill, radius = radius * ring.value)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * ring.value,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (tick.value > 0f) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.30f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.65f)
                lineTo(w * 0.71f, h * 0.38f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick.value, partial, true)
            drawPath(partial, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A soft halo that breathes behind something "live", e.g. the current step of a job. */
@Composable
fun Modifier.pulsingHalo(color: Color, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "halo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "haloProgress",
    )
    return drawBehind {
        val base = size.minDimension / 2
        drawCircle(color.copy(alpha = 0.35f * (1f - progress)), radius = base * (1f + 0.7f * progress))
    }
}

/** Sinks slightly while pressed and springs back: tactile feedback for tappable surfaces. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = SahyogMotion.pressSpring,
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Animates a number up to [target] (e.g. a rupee amount), for counters and totals. */
@Composable
fun animatedCount(target: Long, durationMillis: Int = SahyogMotion.LONG): Long {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return value.value.toLong()
}
