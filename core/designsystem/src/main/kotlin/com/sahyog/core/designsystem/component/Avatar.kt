package com.sahyog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyog.core.designsystem.theme.SahyogTheme

/**
 * Circular initials avatar. Photo support (Coil) arrives with worker profiles;
 * initials remain the fallback while images load or when none is uploaded.
 * Decorative: the person's name is always shown next to it, so it's hidden from TalkBack.
 */
@Composable
fun SahyogAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val initials = remember(name) { initialsOf(name) }
    Box(
        modifier = modifier
            .size(size)
            .background(SahyogTheme.colors.primaryContainer, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = SahyogTheme.colors.onPrimaryContainer,
            style = SahyogTheme.typography.titleMedium.copy(fontSize = (size.value * 0.36f).sp),
        )
    }
}

internal fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "?" }
