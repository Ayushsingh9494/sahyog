package com.sahyog.core.designsystem.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Screen-to-screen motion. Opening a screen slides it in a little from the reading direction
 * while the old one eases back; going back reverses it. Tabs simply cross-fade, since they
 * are siblings rather than a step deeper. Offsets are small (a tenth of the width) so the
 * motion reads as direction, not as travel.
 */
object SahyogTransitions {
    private const val DURATION = 300
    private fun <T> spec() = tween<T>(DURATION, easing = FastOutSlowInEasing)

    val push: EnterTransition =
        fadeIn(spec()) + slideInHorizontally(spec()) { width -> width / 10 }
    val pushExit: ExitTransition =
        fadeOut(spec()) + slideOutHorizontally(spec()) { width -> -width / 20 }
    val pop: EnterTransition =
        fadeIn(spec()) + slideInHorizontally(spec()) { width -> -width / 20 }
    val popExit: ExitTransition =
        fadeOut(spec()) + slideOutHorizontally(spec()) { width -> width / 10 }

    val tabEnter: EnterTransition = fadeIn(tween(220))
    val tabExit: ExitTransition = fadeOut(tween(220))
}
