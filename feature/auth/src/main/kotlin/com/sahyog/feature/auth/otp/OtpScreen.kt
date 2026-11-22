package com.sahyog.feature.auth.otp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogOtpField
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.feature.auth.R
import com.sahyog.core.ui.formatIndianPhone

@Composable
internal fun OtpScreen(
    onNavigateBack: () -> Unit,
    viewModel: OtpViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OtpContent(
        uiState = uiState,
        onCodeChange = viewModel::onCodeChange,
        onVerify = viewModel::verify,
        onResend = viewModel::resend,
        onChangeNumber = onNavigateBack,
    )
}

@Composable
internal fun OtpContent(
    uiState: OtpUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = { SahyogTopAppBar(title = "", onNavigateBack = onChangeNumber) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = SahyogSpacing.screenGutter),
        ) {
            Text(
                text = stringResource(R.string.auth_otp_title),
                style = SahyogTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(SahyogSpacing.sm))
            Text(
                text = stringResource(R.string.auth_otp_subtitle, formatIndianPhone(uiState.phoneNumber)),
                style = SahyogTheme.typography.bodyLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            SahyogTextButton(
                text = stringResource(R.string.auth_otp_change_number),
                onClick = onChangeNumber,
                enabled = !uiState.isVerifying,
            )
            Spacer(Modifier.height(SahyogSpacing.lg))

            SahyogOtpField(
                value = uiState.code,
                onValueChange = onCodeChange,
                isError = uiState.errorRes != null,
                enabled = !uiState.isVerifying,
                focusRequester = focusRequester,
                onDone = onVerify,
            )

            Spacer(Modifier.height(SahyogSpacing.md))
            StatusLine(uiState)

            Spacer(Modifier.height(SahyogSpacing.xl))
            ResendRow(uiState, onResend)
        }
    }
}

@Composable
private fun StatusLine(uiState: OtpUiState) {
    // Announced by TalkBack when it changes (errors, "verifying", "code resent").
    val modifier = Modifier
        .height(24.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }
    when {
        uiState.isVerifying -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(SahyogSpacing.sm))
            Text(stringResource(R.string.auth_otp_verifying), style = SahyogTheme.typography.bodyMedium)
        }
        uiState.errorRes != null -> Text(
            text = stringResource(uiState.errorRes),
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.error,
            modifier = modifier,
        )
        uiState.infoRes != null -> Text(
            text = stringResource(uiState.infoRes),
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.extendedColors.success,
            modifier = modifier,
        )
        else -> Spacer(modifier)
    }
}

@Composable
private fun ResendRow(uiState: OtpUiState, onResend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = stringResource(R.string.auth_otp_not_received),
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.onSurfaceVariant,
        )
        if (uiState.resendSecondsLeft > 0) {
            Text(
                text = " " + stringResource(R.string.auth_otp_resend_in, uiState.resendSecondsLeft),
                style = SahyogTheme.typography.labelLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        } else {
            SahyogTextButton(
                text = stringResource(R.string.auth_otp_resend),
                onClick = onResend,
                enabled = uiState.canResend,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OtpPreview() {
    SahyogTheme {
        OtpContent(
            uiState = OtpUiState(phoneNumber = "9876543210", code = "123", resendSecondsLeft = 24),
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onChangeNumber = {},
        )
    }
}
