package com.sahyog.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.R
import com.sahyog.core.designsystem.theme.SahyogSize
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

private val ButtonPadding = PaddingValues(horizontal = SahyogSpacing.xxl, vertical = SahyogSpacing.md)

/**
 * Primary call to action. One per screen, ideally.
 *
 * @param loading Shows a spinner and ignores taps while keeping the button's size
 * stable, so layouts don't jump while a request is in flight.
 */
@Composable
fun SahyogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .defaultMinSize(minHeight = SahyogSize.buttonHeight)
            .loadingSemantics(loading),
        enabled = enabled,
        shape = SahyogTheme.shapes.medium,
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text, loading, leadingIcon)
    }
}

/** Secondary action, shown alongside a [SahyogButton]. */
@Composable
fun SahyogOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .defaultMinSize(minHeight = SahyogSize.buttonHeight)
            .loadingSemantics(loading),
        enabled = enabled,
        shape = SahyogTheme.shapes.medium,
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text, loading, leadingIcon)
    }
}

/** Low-emphasis action, e.g. "Skip" or "Resend code". */
@Composable
fun SahyogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = SahyogSize.minTouchTarget),
        enabled = enabled,
        shape = SahyogTheme.shapes.medium,
    ) {
        Text(text = text, style = SahyogTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    Box(contentAlignment = Alignment.Center) {
        // Content stays laid out (invisible) while loading so the width doesn't change.
        Row(
            modifier = Modifier.alpha(if (loading) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(SahyogSpacing.sm))
            }
            Text(text = text, style = SahyogTheme.typography.labelLarge)
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun Modifier.loadingSemantics(loading: Boolean): Modifier {
    if (!loading) return this
    val description = stringResource(R.string.designsystem_loading)
    return semantics { stateDescription = description }
}
