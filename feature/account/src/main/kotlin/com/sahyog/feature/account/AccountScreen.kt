package com.sahyog.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogSegmentedControl
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.ThemePreference
import com.sahyog.core.model.User
import com.sahyog.core.model.UserRole
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.ui.formatIndianPhone

/**
 * @param onOpenDesignCatalog Developer-only entry; pass null to hide it (release builds).
 */
@Composable
fun AccountScreen(
    onOpenDesignCatalog: (() -> Unit)?,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AccountContent(
        uiState = uiState,
        onSwitchRole = viewModel::onSwitchRole,
        onThemeChange = viewModel::onThemeChange,
        onSignOut = viewModel::onSignOut,
        onOpenDesignCatalog = onOpenDesignCatalog,
    )
}

@Composable
internal fun AccountContent(
    uiState: AccountUiState,
    onSwitchRole: (UserRole) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onSignOut: () -> Unit,
    onOpenDesignCatalog: (() -> Unit)?,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.account_title)) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        when (uiState) {
            AccountUiState.Loading -> SahyogLoadingView(Modifier.padding(padding))
            is AccountUiState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xl),
            ) {
                ProfileCard(uiState.user)

                if (uiState.canSwitchRole) {
                    Section(stringResource(R.string.account_mode_title)) {
                        Text(
                            stringResource(R.string.account_mode_support),
                            style = SahyogTheme.typography.bodyMedium,
                            color = SahyogTheme.colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(SahyogSpacing.md))
                        SahyogSegmentedControl(
                            options = UserRole.entries.filter { it in uiState.user.roles },
                            selected = uiState.activeRole,
                            onSelect = onSwitchRole,
                            label = { stringResource(it.labelRes()) },
                        )
                    }
                }

                Section(stringResource(R.string.account_theme_title)) {
                    SahyogSegmentedControl(
                        options = ThemePreference.entries,
                        selected = uiState.theme,
                        onSelect = onThemeChange,
                        label = { stringResource(it.labelRes()) },
                    )
                }

                if (onOpenDesignCatalog != null) {
                    Section(stringResource(R.string.account_developer_title)) {
                        SahyogCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenDesignCatalog,
                            contentPadding = PaddingValues(horizontal = SahyogSpacing.lg, vertical = SahyogSpacing.md),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.account_design_catalog),
                                    style = SahyogTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(SahyogIcons.Forward, contentDescription = null)
                            }
                        }
                    }
                }

                SahyogOutlinedButton(
                    text = stringResource(R.string.account_sign_out),
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(SahyogSpacing.lg))
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.account_sign_out_confirm_body)) },
            confirmButton = {
                SahyogTextButton(
                    text = stringResource(R.string.account_sign_out),
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                )
            },
            dismissButton = {
                SahyogTextButton(text = stringResource(R.string.account_cancel), onClick = { confirmSignOut = false })
            },
        )
    }
}

@Composable
private fun ProfileCard(user: User) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogAvatar(name = user.name.orEmpty(), size = 56.dp)
            Spacer(Modifier.width(SahyogSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(user.name.orEmpty(), style = SahyogTheme.typography.titleLarge)
                Text(
                    formatIndianPhone(user.phoneNumber),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
            user.roles.sortedBy { it.ordinal }.forEach { role ->
                SahyogStatusBadge(stringResource(role.labelRes()), StatusTone.Brand)
            }
            user.workerStatus?.let { status ->
                val (labelRes, tone) = status.badge()
                SahyogStatusBadge(stringResource(labelRes), tone)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = SahyogTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SahyogSpacing.sm))
        content()
    }
}

private fun UserRole.labelRes(): Int = when (this) {
    UserRole.Customer -> R.string.account_role_customer
    UserRole.Worker -> R.string.account_role_worker
    UserRole.Admin -> R.string.account_role_admin
}

private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.System -> R.string.account_theme_system
    ThemePreference.Light -> R.string.account_theme_light
    ThemePreference.Dark -> R.string.account_theme_dark
}

private fun WorkerVerificationStatus.badge(): Pair<Int, StatusTone> = when (this) {
    WorkerVerificationStatus.NotSubmitted -> R.string.account_worker_not_submitted to StatusTone.Warning
    WorkerVerificationStatus.Pending -> R.string.account_worker_pending to StatusTone.Info
    WorkerVerificationStatus.Verified -> R.string.account_worker_verified to StatusTone.Success
    WorkerVerificationStatus.Rejected -> R.string.account_worker_rejected to StatusTone.Error
}

@Preview(showBackground = true)
@Composable
private fun AccountPreview() {
    SahyogTheme {
        AccountContent(
            uiState = AccountUiState.Ready(
                user = User(
                    id = "1",
                    phoneNumber = "9876543210",
                    name = "Asha Verma",
                    roles = setOf(UserRole.Customer, UserRole.Worker),
                    workerStatus = WorkerVerificationStatus.NotSubmitted,
                ),
                activeRole = UserRole.Customer,
                theme = ThemePreference.System,
            ),
            onSwitchRole = {},
            onThemeChange = {},
            onSignOut = {},
            onOpenDesignCatalog = {},
        )
    }
}
