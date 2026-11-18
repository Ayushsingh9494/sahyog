package com.sahyog.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4dp-based spacing scale. Use these instead of raw dp values in screens. */
object SahyogSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    /** Horizontal gutter for screen content. */
    val screenGutter = 20.dp
}

/** Sahyog mostly uses tonal surfaces and borders, so shadows are kept subtle. */
object SahyogElevation {
    val none = 0.dp
    val low = 1.dp
    val medium = 3.dp
    val high = 6.dp
}

/** Minimum touch target and control heights. Generous for low-dexterity / outdoor use. */
object SahyogSize {
    val minTouchTarget = 48.dp
    val buttonHeight = 52.dp
    val iconSmall = 16.dp
    val icon = 24.dp
}

internal val SahyogShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
