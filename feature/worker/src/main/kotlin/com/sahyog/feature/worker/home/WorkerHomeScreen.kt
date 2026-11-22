package com.sahyog.feature.worker.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.WorkerProfile
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.worker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often to check for the cooperative's decision while verification is pending. */
private const val VERIFICATION_POLL_MS = 10_000L

@Composable
fun WorkerHomeScreen(
    onSetUpProfile: () -> Unit,
    onAddId: () -> Unit,
    viewModel: WorkerHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is WorkerHomeEvent.Failed -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()))
            }
        }
    }

    val pending = (uiState as? WorkerHomeUiState.Ready)?.profile?.verificationStatus == WorkerVerificationStatus.Pending
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(pending, lifecycleOwner) {
        if (!pending) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(VERIFICATION_POLL_MS)
                viewModel.onAutoRefresh()
            }
        }
    }

    WorkerHomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::onRetry,
        onSetUpProfile = onSetUpProfile,
        onAddId = onAddId,
        onAvailabilityChange = viewModel::onAvailabilityChange,
    )
}

@Composable
internal fun WorkerHomeContent(
    uiState: WorkerHomeUiState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onSetUpProfile: () -> Unit,
    onAddId: () -> Unit,
    onAvailabilityChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.worker_home_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            WorkerHomeUiState.Loading -> SahyogLoadingView(modifier)
            is WorkerHomeUiState.Error -> SahyogErrorView(
                title = stringResource(R.string.worker_home_error_title),
                message = stringResource(uiState.error.messageRes()),
                onRetry = onRetry,
                modifier = modifier,
            )
            WorkerHomeUiState.NeedsProfile -> HomeList(modifier) {
                ActionCard(
                    badge = { SahyogStatusBadge(stringResource(R.string.worker_setup_action), StatusTone.Warning) },
                    title = stringResource(R.string.worker_setup_title),
                    body = stringResource(R.string.worker_setup_body),
                    actionLabel = stringResource(R.string.worker_setup_action),
                    onAction = onSetUpProfile,
                )
            }
            is WorkerHomeUiState.Ready -> HomeList(modifier) {
                VerificationCard(uiState.profile, onAddId)
                if (uiState.profile.verificationStatus == WorkerVerificationStatus.Verified) {
                    AvailabilityCard(uiState.profile.isAvailable, uiState.isUpdatingAvailability, onAvailabilityChange)
                }
                SahyogTextButton(
                    text = stringResource(R.string.worker_edit_profile),
                    onClick = onSetUpProfile,
                )
                SahyogEmptyView(
                    title = stringResource(R.string.worker_no_jobs_title),
                    message = stringResource(R.string.worker_no_jobs_message),
                    icon = SahyogIcons.Jobs,
                )
            }
        }
    }
}

@Composable
private fun HomeList(modifier: Modifier, content: @Composable () -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) { content() }
        }
    }
}

@Composable
private fun VerificationCard(profile: WorkerProfile, onAddId: () -> Unit) {
    when (profile.verificationStatus) {
        WorkerVerificationStatus.NotSubmitted -> ActionCard(
            badge = { SahyogStatusBadge(stringResource(R.string.worker_verify_needed_action), StatusTone.Warning) },
            title = stringResource(R.string.worker_verify_needed_title),
            body = stringResource(R.string.worker_verify_needed_body),
            actionLabel = stringResource(R.string.worker_verify_needed_action),
            onAction = onAddId,
        )
        WorkerVerificationStatus.Pending -> ActionCard(
            badge = {
                SahyogStatusBadge(stringResource(R.string.worker_verify_pending_title), StatusTone.Info, icon = SahyogIcons.Info)
            },
            title = stringResource(R.string.worker_verify_pending_title),
            body = stringResource(R.string.worker_verify_pending_body),
        )
        WorkerVerificationStatus.Rejected -> ActionCard(
            badge = { SahyogStatusBadge(stringResource(R.string.worker_verify_rejected_title), StatusTone.Error) },
            title = stringResource(R.string.worker_verify_rejected_title),
            body = stringResource(R.string.worker_verify_rejected_body),
            actionLabel = stringResource(R.string.worker_verify_needed_action),
            onAction = onAddId,
        )
        WorkerVerificationStatus.Verified -> ActionCard(
            badge = {
                SahyogStatusBadge(stringResource(R.string.worker_verified_title), StatusTone.Success, icon = SahyogIcons.Verified)
            },
            title = stringResource(R.string.worker_verified_title),
            body = stringResource(R.string.worker_verified_body),
        )
    }
}

@Composable
private fun ActionCard(
    badge: @Composable () -> Unit,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        badge()
        Spacer(Modifier.height(SahyogSpacing.sm))
        Text(title, style = SahyogTheme.typography.titleMedium)
        Spacer(Modifier.height(SahyogSpacing.xs))
        Text(body, style = SahyogTheme.typography.bodyMedium, color = SahyogTheme.colors.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(SahyogSpacing.md))
            SahyogButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AvailabilityCard(available: Boolean, updating: Boolean, onChange: (Boolean) -> Unit) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // The whole row toggles, so the target is large and screen readers announce one switch.
            modifier = Modifier.toggleable(
                value = available,
                enabled = !updating,
                role = Role.Switch,
                onValueChange = onChange,
            ),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.worker_accepting_jobs), style = SahyogTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        if (available) R.string.worker_accepting_jobs_on else R.string.worker_accepting_jobs_off,
                    ),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(SahyogSpacing.md))
            Switch(checked = available, onCheckedChange = null, enabled = !updating)
        }
    }
}
