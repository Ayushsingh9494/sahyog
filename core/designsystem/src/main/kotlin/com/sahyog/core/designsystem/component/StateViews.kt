package com.sahyog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.R
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/*
 * The three non-content states every data-driven screen must handle.
 * Screens render exactly one of: Loading / Empty / Error / their content.
 */

@Composable
fun SahyogLoadingView(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.designsystem_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = SahyogTheme.colors.primary)
    }
}

@Composable
fun SahyogEmptyView(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = SahyogIcons.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    MessageView(
        title = title,
        message = message,
        icon = icon,
        iconTint = SahyogTheme.colors.onPrimaryContainer,
        iconBackground = SahyogTheme.colors.primaryContainer,
        modifier = modifier,
    ) {
        if (actionLabel != null && onAction != null) {
            SahyogOutlinedButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun SahyogErrorView(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    MessageView(
        title = title,
        message = message,
        icon = SahyogIcons.Warning,
        iconTint = SahyogTheme.colors.onErrorContainer,
        iconBackground = SahyogTheme.colors.errorContainer,
        modifier = modifier,
    ) {
        SahyogButton(
            text = stringResource(R.string.designsystem_retry),
            onClick = onRetry,
            leadingIcon = SahyogIcons.Retry,
        )
    }
}

@Composable
private fun MessageView(
    title: String,
    message: String?,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SahyogSpacing.xxxl, vertical = SahyogSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(SahyogSpacing.xl))
        Text(
            text = title,
            style = SahyogTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(SahyogSpacing.sm))
            Text(
                text = message,
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(SahyogSpacing.xxl))
        action()
    }
}
