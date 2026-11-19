package com.sahyog.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogSelectableCard
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.UserRole

/** Shown once, after the first sign-in: collects a display name and the roles the person wants. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingContent(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onRoleToggle = viewModel::onRoleToggle,
        onSubmit = viewModel::onSubmit,
        onUseDifferentNumber = viewModel::onUseDifferentNumber,
    )
}

@Composable
internal fun OnboardingContent(
    uiState: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onRoleToggle: (UserRole, Boolean) -> Unit,
    onSubmit: () -> Unit,
    onUseDifferentNumber: () -> Unit,
) {
    Surface(color = SahyogTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = SahyogSpacing.screenGutter),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = SahyogSpacing.xxxl),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = SahyogTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(SahyogSpacing.sm))
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = SahyogTheme.typography.bodyLarge,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(SahyogSpacing.xxl))

                SahyogTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.onboarding_name_label),
                    supportingText = stringResource(R.string.onboarding_name_support),
                    errorText = if (uiState.showNameError && !uiState.isNameValid) {
                        stringResource(R.string.onboarding_name_error)
                    } else {
                        null
                    },
                    enabled = !uiState.isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.PersonFullName },
                )

                Spacer(Modifier.height(SahyogSpacing.xxl))
                Text(
                    text = stringResource(R.string.onboarding_roles_title),
                    style = SahyogTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.onboarding_roles_support),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(SahyogSpacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
                    SahyogSelectableCard(
                        title = stringResource(R.string.onboarding_role_customer_title),
                        description = stringResource(R.string.onboarding_role_customer_body),
                        icon = SahyogIcons.Home,
                        selected = UserRole.Customer in uiState.roles,
                        onSelectedChange = { onRoleToggle(UserRole.Customer, it) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SahyogSelectableCard(
                        title = stringResource(R.string.onboarding_role_worker_title),
                        description = stringResource(R.string.onboarding_role_worker_body),
                        icon = SahyogIcons.Service,
                        selected = UserRole.Worker in uiState.roles,
                        onSelectedChange = { onRoleToggle(UserRole.Worker, it) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (uiState.errorRes != null) {
                    Spacer(Modifier.height(SahyogSpacing.lg))
                    Text(
                        text = stringResource(uiState.errorRes),
                        color = SahyogTheme.colors.error,
                        style = SahyogTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            SahyogButton(
                text = stringResource(R.string.onboarding_cta),
                onClick = onSubmit,
                enabled = (uiState.roles.isNotEmpty() && uiState.name.isNotBlank()) || uiState.isSubmitting,
                loading = uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SahyogSpacing.lg),
            )
            SahyogTextButton(
                text = stringResource(R.string.onboarding_different_number),
                onClick = onUseDifferentNumber,
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = SahyogSpacing.sm),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    SahyogTheme {
        OnboardingContent(
            uiState = OnboardingUiState(name = "Asha", roles = setOf(UserRole.Customer)),
            onNameChange = {},
            onRoleToggle = { _, _ -> },
            onSubmit = {},
            onUseDifferentNumber = {},
        )
    }
}
