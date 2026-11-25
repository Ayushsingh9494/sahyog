package com.sahyog.feature.admin.detail

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.common.result.AppError
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogRatingSummary
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.AdminReason
import com.sahyog.core.model.AdminWorkerDetail
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.booking.formatDayMonth
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.core.ui.catalog.priceUnitSuffix
import com.sahyog.core.ui.formatIndianPhone
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.admin.R
import com.sahyog.feature.admin.component.DocumentViewer
import com.sahyog.feature.admin.component.ReasonSheet
import com.sahyog.feature.admin.component.SecureScreen
import com.sahyog.feature.admin.component.SectionHeader
import com.sahyog.feature.admin.component.StatusBadge
import com.sahyog.feature.admin.component.StatusChangeRow
import com.sahyog.feature.admin.component.categoriesLabel
import com.sahyog.feature.admin.component.decodeDocument
import com.sahyog.feature.admin.component.documentLabel
import com.sahyog.feature.admin.component.relativeTime
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun AdminWorkerScreen(
    viewModel: AdminWorkerViewModel,
    onNavigateBack: () -> Unit,
    /** After a decision: go back, and tell the previous screen to refresh. */
    onDecided: (message: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is AdminWorkerEvent.Done -> onDecided(
                resources.getString(
                    when (event.action) {
                        AdminAction.Approve -> R.string.admin_done_approved
                        AdminAction.Reject -> R.string.admin_done_rejected
                        AdminAction.Revoke -> R.string.admin_done_revoked
                    },
                    event.workerName,
                ),
            )
            is AdminWorkerEvent.Failed -> scope.launch {
                val message = when (event.error) {
                    is AppError.Validation -> R.string.admin_error_already_decided
                    AppError.Forbidden -> R.string.admin_error_forbidden
                    else -> event.error.messageRes()
                }
                snackbarHostState.showSnackbar(resources.getString(message))
            }
        }
    }
    AdminWorkerContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::onRetry,
        onRetryDocument = viewModel::onRetryDocument,
        onApprove = viewModel::onApprove,
        onReject = viewModel::onReject,
        onRevoke = viewModel::onRevoke,
    )
}

@Composable
internal fun AdminWorkerContent(
    uiState: AdminWorkerUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryDocument: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onRevoke: (String) -> Unit,
) {
    var confirmApprove by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<AdminAction?>(null) }
    val ready = uiState as? AdminWorkerUiState.Ready
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.admin_worker_title), onNavigateBack = onNavigateBack) },
        bottomBar = {
            if (ready != null && ready.actions.isNotEmpty()) {
                ActionBar(ready, onApprove = { confirmApprove = true }, onAskReason = { sheet = it })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            AdminWorkerUiState.Loading -> SahyogLoadingView(modifier)
            is AdminWorkerUiState.Error -> SahyogErrorView(
                title = stringResource(R.string.admin_error_title),
                message = stringResource(uiState.error.messageRes()),
                onRetry = onRetry,
                modifier = modifier,
            )
            is AdminWorkerUiState.Ready -> Review(uiState, onRetryDocument, modifier)
        }
    }

    val name = ready?.detail?.worker?.name.orEmpty()
    if (confirmApprove) {
        AlertDialog(
            onDismissRequest = { confirmApprove = false },
            title = { Text(stringResource(R.string.admin_approve_confirm_title, name)) },
            text = { Text(stringResource(R.string.admin_approve_confirm_body)) },
            confirmButton = {
                SahyogTextButton(
                    text = stringResource(R.string.admin_approve),
                    onClick = {
                        confirmApprove = false
                        onApprove()
                    },
                )
            },
            dismissButton = {
                SahyogTextButton(text = stringResource(R.string.admin_cancel), onClick = { confirmApprove = false })
            },
        )
    }
    when (sheet) {
        AdminAction.Reject -> ReasonSheet(
            title = stringResource(R.string.admin_reject_title, name),
            body = stringResource(R.string.admin_reject_body),
            reasons = listOf(
                AdminReason.PhotoUnclear,
                AdminReason.NameMismatch,
                AdminReason.DocumentNotAccepted,
                AdminReason.DocumentExpired,
                AdminReason.Other,
            ),
            confirmLabel = stringResource(R.string.admin_reject),
            onConfirm = { reason ->
                sheet = null
                onReject(reason)
            },
            onDismiss = { sheet = null },
        )
        AdminAction.Revoke -> ReasonSheet(
            title = stringResource(R.string.admin_revoke_title, name),
            body = stringResource(R.string.admin_revoke_body),
            reasons = listOf(AdminReason.Complaints, AdminReason.DocumentExpired, AdminReason.Other),
            confirmLabel = stringResource(R.string.admin_revoke),
            onConfirm = { reason ->
                sheet = null
                onRevoke(reason)
            },
            onDismiss = { sheet = null },
        )
        else -> Unit
    }
}

@Composable
private fun Review(state: AdminWorkerUiState.Ready, onRetryDocument: () -> Unit, modifier: Modifier) {
    val detail = state.detail
    // Personal data of another person is on screen: keep it out of screenshots and Recents.
    SecureScreen()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.lg),
    ) {
        item(key = "header") { Header(detail) }
        item(key = "document") { DocumentSection(state, onRetryDocument) }
        if (detail.worker.status == WorkerVerificationStatus.Pending) {
            item(key = "checklist") { Checklist(detail.worker.name) }
        }
        if (detail.services.isNotEmpty()) item(key = "services") { Services(detail) }
        item(key = "profile") { ProfileFacts(detail) }
        item(key = "history") { History(detail, state.now) }
    }
}

@Composable
private fun Header(detail: AdminWorkerDetail) {
    val worker = detail.worker
    val context = LocalContext.current
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogAvatar(name = worker.name, size = 64.dp)
            Spacer(Modifier.width(SahyogSpacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xxs)) {
                Text(worker.name, style = SahyogTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    formatIndianPhone(worker.phoneNumber),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
                StatusBadge(worker.status)
            }
        }
        val categories = categoriesLabel(worker.categoryIds)
        if (categories.isNotBlank()) {
            Spacer(Modifier.height(SahyogSpacing.md))
            Text(categories, style = SahyogTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        HorizontalDivider(color = SahyogTheme.colors.outlineVariant)
        Spacer(Modifier.height(SahyogSpacing.md))
        Row(Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.admin_stat_rating), Modifier.weight(1f)) {
                if (worker.ratingCount > 0) {
                    SahyogRatingSummary(worker.rating, worker.ratingCount)
                } else {
                    Text("—", style = SahyogTheme.typography.titleMedium)
                }
            }
            Stat(stringResource(R.string.admin_stat_jobs), Modifier.weight(1f)) {
                Text(worker.jobsCompleted.toString(), style = SahyogTheme.typography.titleMedium)
            }
            Stat(stringResource(R.string.admin_stat_experience), Modifier.weight(1f)) {
                Text(
                    detail.yearsExperience?.let { pluralStringResource(R.plurals.admin_years, it, it) } ?: "—",
                    style = SahyogTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.admin_joined,
                    formatDayMonth(worker.joinedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                ),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SahyogOutlinedButton(
                text = stringResource(R.string.admin_call),
                leadingIcon = SahyogIcons.Phone,
                // Opens the dialer with the number filled in; nothing is called without the admin.
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${worker.phoneNumber}".toUri())) },
            )
        }
    }
}

@Composable
private fun Stat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        value()
        Text(label, style = SahyogTheme.typography.bodySmall, color = SahyogTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun DocumentSection(state: AdminWorkerUiState.Ready, onRetry: () -> Unit) {
    val detail = state.detail
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
        SectionHeader(stringResource(R.string.admin_section_document))
        val type = detail.documentType
        val submitted = detail.documentSubmittedAt
        if (type == null || submitted == null) {
            Text(
                stringResource(R.string.admin_document_none),
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
            return@Column
        }
        val typeLabel = documentLabel(type)
        Text(
            text = stringResource(R.string.admin_document_submitted, typeLabel, relativeTime(submitted, state.now)),
            style = SahyogTheme.typography.bodyMedium,
        )
        val frame = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 260.dp)
            .clip(SahyogTheme.shapes.large)
            .background(SahyogTheme.colors.surfaceContainerHighest)
        when (val document = state.document) {
            DocumentState.None -> Unit
            DocumentState.Loading -> Box(frame.height(200.dp), contentAlignment = Alignment.Center) {
                SahyogLoadingView(Modifier.size(48.dp))
            }
            is DocumentState.Failed -> Column(
                frame.padding(SahyogSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.admin_document_failed), style = SahyogTheme.typography.bodyMedium)
                SahyogTextButton(text = stringResource(com.sahyog.core.designsystem.R.string.designsystem_retry), onClick = onRetry)
            }
            is DocumentState.Loaded -> {
                val image = remember(document) { decodeDocument(document.bytes) }
                val description = stringResource(R.string.admin_document_description, typeLabel)
                if (image == null) {
                    Box(frame.padding(SahyogSpacing.lg), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.admin_document_unreadable), style = SahyogTheme.typography.bodyMedium)
                    }
                } else {
                    Image(
                        bitmap = image,
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        modifier = frame.clickable(role = Role.Image, onClickLabel = stringResource(R.string.admin_document_tap)) {
                            fullScreen = true
                        },
                    )
                    Text(
                        stringResource(R.string.admin_document_tap),
                        style = SahyogTheme.typography.labelMedium,
                        color = SahyogTheme.colors.primary,
                    )
                    if (fullScreen) DocumentViewer(image, description, onDismiss = { fullScreen = false })
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                SahyogIcons.Lock,
                contentDescription = null,
                tint = SahyogTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp),
            )
            Spacer(Modifier.width(SahyogSpacing.xs))
            Text(
                stringResource(R.string.admin_document_private),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** What to check, so every admin approves to the same standard. */
@Composable
private fun Checklist(name: String) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.admin_section_checklist),
            style = SahyogTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SahyogSpacing.sm))
        listOf(
            stringResource(R.string.admin_check_name, name),
            stringResource(R.string.admin_check_photo),
            stringResource(R.string.admin_check_valid),
        ).forEach { line ->
            Row(Modifier.padding(vertical = SahyogSpacing.xs), verticalAlignment = Alignment.Top) {
                Icon(
                    SahyogIcons.VerifiedOutlined,
                    contentDescription = null,
                    tint = SahyogTheme.colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(SahyogSpacing.sm))
                Text(line, style = SahyogTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Services(detail: AdminWorkerDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
        SectionHeader(stringResource(R.string.admin_section_services))
        SahyogCard(modifier = Modifier.fillMaxWidth()) {
            detail.services.forEachIndexed { index, service ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = SahyogSpacing.sm), color = SahyogTheme.colors.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(service.name, style = SahyogTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        formatRupees(service.startingPrice) + priceUnitSuffix(service.unit),
                        style = SahyogTheme.typography.labelLarge,
                        color = SahyogTheme.colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileFacts(detail: AdminWorkerDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
        SectionHeader(stringResource(R.string.admin_section_profile))
        SahyogCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md)) {
                detail.worker.locality?.let { Fact(stringResource(R.string.admin_area), it) }
                if (detail.languages.isNotEmpty()) Fact(stringResource(R.string.admin_languages), detail.languages.joinToString(", "))
                detail.about?.let { Fact(stringResource(R.string.admin_about), it) }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(
            label,
            style = SahyogTheme.typography.bodyMedium,
            color = SahyogTheme.colors.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = SahyogTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun History(detail: AdminWorkerDetail, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
        SectionHeader(stringResource(R.string.admin_section_history))
        SahyogCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = SahyogSpacing.lg, vertical = SahyogSpacing.sm),
        ) {
            if (detail.history.isEmpty()) {
                Text(
                    stringResource(R.string.admin_history_empty),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = SahyogSpacing.md),
                )
            }
            detail.history.forEach { StatusChangeRow(it, now, showWorker = false) }
        }
    }
}

/** Sticky decision bar: Reject + Approve for a pending worker, Revoke for a verified one. */
@Composable
private fun ActionBar(state: AdminWorkerUiState.Ready, onApprove: () -> Unit, onAskReason: (AdminAction) -> Unit) {
    Surface(color = SahyogTheme.colors.surfaceContainer, tonalElevation = SahyogElevation.medium) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
        ) {
            val busy = state.acting != null
            state.actions.forEach { action ->
                when (action) {
                    AdminAction.Approve -> SahyogButton(
                        text = stringResource(R.string.admin_approve),
                        onClick = onApprove,
                        enabled = !busy,
                        loading = state.acting == AdminAction.Approve,
                        leadingIcon = SahyogIcons.Check,
                        modifier = Modifier.weight(1f),
                    )
                    AdminAction.Reject -> SahyogOutlinedButton(
                        text = stringResource(R.string.admin_reject),
                        onClick = { onAskReason(AdminAction.Reject) },
                        enabled = !busy,
                        loading = state.acting == AdminAction.Reject,
                        leadingIcon = SahyogIcons.Close,
                        modifier = Modifier.weight(1f),
                    )
                    AdminAction.Revoke -> SahyogOutlinedButton(
                        text = stringResource(R.string.admin_revoke),
                        onClick = { onAskReason(AdminAction.Revoke) },
                        enabled = !busy,
                        loading = state.acting == AdminAction.Revoke,
                        leadingIcon = SahyogIcons.Warning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
