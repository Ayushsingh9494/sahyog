package com.sahyog.feature.auth.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.feature.auth.R

@Composable
internal fun PhoneEntryScreen(
    onNavigateBack: () -> Unit,
    onOtpSent: (phoneNumber: String) -> Unit,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PhoneEntryEvent.OtpSent -> onOtpSent(event.phoneNumber)
        }
    }
    PhoneEntryContent(
        uiState = uiState,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onSubmit = viewModel::onSubmit,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
internal fun PhoneEntryContent(
    uiState: PhoneEntryUiState,
    onPhoneNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val errorText = when {
        uiState.errorRes != null -> stringResource(uiState.errorRes)
        uiState.isInvalidComplete -> stringResource(R.string.auth_phone_error_invalid)
        else -> null
    }

    Scaffold(
        topBar = { SahyogTopAppBar(title = "", onNavigateBack = onNavigateBack) },
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
                text = stringResource(R.string.auth_phone_title),
                style = SahyogTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(SahyogSpacing.sm))
            Text(
                text = stringResource(R.string.auth_phone_subtitle),
                style = SahyogTheme.typography.bodyLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(SahyogSpacing.xxl))
            SahyogTextField(
                value = uiState.phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = stringResource(R.string.auth_phone_label),
                prefix = "+91 ",
                leadingIcon = SahyogIcons.Phone,
                errorText = errorText,
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentType = ContentType.PhoneNumberNational },
            )
            Spacer(Modifier.weight(1f))
            SahyogButton(
                text = stringResource(R.string.auth_phone_cta),
                onClick = onSubmit,
                enabled = uiState.canSubmit || uiState.isSubmitting,
                loading = uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SahyogSpacing.xl),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhoneEntryPreview() {
    SahyogTheme {
        PhoneEntryContent(
            uiState = PhoneEntryUiState(phoneNumber = "98765"),
            onPhoneNumberChange = {},
            onSubmit = {},
            onNavigateBack = {},
        )
    }
}
