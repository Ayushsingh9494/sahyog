package com.sahyog.feature.auth.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogLogo
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.feature.auth.R

@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit) {
    Surface(color = SahyogTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.xxl),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(SahyogSpacing.xxl))
                SahyogLogo(markHeight = 36.dp)
                Spacer(Modifier.height(SahyogSpacing.huge))
                Text(
                    text = stringResource(R.string.auth_welcome_title),
                    style = SahyogTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(SahyogSpacing.md))
                Text(
                    text = stringResource(R.string.auth_welcome_subtitle),
                    style = SahyogTheme.typography.bodyLarge,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(SahyogSpacing.xxxl))
                Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xl)) {
                    ValuePoint(
                        icon = SahyogIcons.Verified,
                        title = stringResource(R.string.auth_welcome_point_verified_title),
                        body = stringResource(R.string.auth_welcome_point_verified_body),
                    )
                    ValuePoint(
                        icon = SahyogIcons.Star,
                        title = stringResource(R.string.auth_welcome_point_fair_title),
                        body = stringResource(R.string.auth_welcome_point_fair_body),
                    )
                    ValuePoint(
                        icon = SahyogIcons.Account,
                        title = stringResource(R.string.auth_welcome_point_coop_title),
                        body = stringResource(R.string.auth_welcome_point_coop_body),
                    )
                }
            }
            Spacer(Modifier.height(SahyogSpacing.xl))
            SahyogButton(
                text = stringResource(R.string.auth_welcome_cta),
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ValuePoint(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SahyogTheme.colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = SahyogTheme.colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(SahyogSpacing.lg))
        Column {
            Text(title, style = SahyogTheme.typography.titleMedium)
            Text(body, style = SahyogTheme.typography.bodyMedium, color = SahyogTheme.colors.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    SahyogTheme { WelcomeScreen(onGetStarted = {}) }
}
