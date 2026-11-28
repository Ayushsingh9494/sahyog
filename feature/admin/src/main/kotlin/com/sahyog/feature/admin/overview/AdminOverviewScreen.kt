package com.sahyog.feature.admin.overview

import com.sahyog.core.ui.catalog.formatRupees
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.AdminOverview
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.admin.R
import com.sahyog.feature.admin.component.PersonCard
import com.sahyog.feature.admin.component.SectionHeader
import com.sahyog.feature.admin.component.StatTile
import com.sahyog.feature.admin.component.StatusChangeRow
import com.sahyog.feature.admin.component.categoriesLabel
import com.sahyog.feature.admin.component.relativeTime
import java.time.Instant
import java.util.Locale

@Composable
fun AdminOverviewScreen(
    onOpenQueue: () -> Unit,
    onOpenWorker: (String) -> Unit,
    viewModel: AdminOverviewViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AdminOverviewContent(uiState, viewModel::onRefresh, onOpenQueue, onOpenWorker, snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminOverviewContent(
    uiState: AdminOverviewUiState,
    onRefresh: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenWorker: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            SahyogTopAppBar(
                title = stringResource(R.string.admin_overview_title),
                actions = {
                    SahyogStatusBadge(
                        text = stringResource(R.string.admin_console_badge),
                        tone = StatusTone.Brand,
                        icon = SahyogIcons.Lock,
                        modifier = Modifier.padding(end = SahyogSpacing.md),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            AdminOverviewUiState.Loading -> SahyogLoadingView(modifier)
            is AdminOverviewUiState.Error -> SahyogErrorView(
                title = stringResource(R.string.admin_error_title),
                message = stringResource(uiState.error.messageRes()),
                onRetry = onRefresh,
                modifier = modifier,
            )
            is AdminOverviewUiState.Ready -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
            ) {
                Dashboard(uiState, onOpenQueue, onOpenWorker)
            }
        }
    }
}

@Composable
private fun Dashboard(state: AdminOverviewUiState.Ready, onOpenQueue: () -> Unit, onOpenWorker: (String) -> Unit) {
    val overview = state.overview
    val now = Instant.now()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.lg),
    ) {
        item(key = "attention") { AttentionPanel(overview.pendingVerifications, onOpenQueue) }

        if (state.nextInQueue.isNotEmpty()) {
            item(key = "queue") {
                Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                    SectionHeader(stringResource(R.string.admin_section_queue_preview)) {
                        SahyogTextButton(text = stringResource(R.string.admin_see_all), onClick = onOpenQueue)
                    }
                    state.nextInQueue.forEach { request ->
                        PersonCard(
                            name = request.name,
                            subtitle = listOfNotNull(categoriesLabel(request.categoryIds).ifBlank { null }, request.locality)
                                .joinToString(" · "),
                            onClick = { onOpenWorker(request.workerId) },
                            trailing = {
                                Text(
                                    text = relativeTime(request.submittedAt, now),
                                    style = SahyogTheme.typography.labelMedium,
                                    color = SahyogTheme.colors.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }

        item(key = "cooperative") {
            StatGrid(
                title = stringResource(R.string.admin_section_cooperative),
                tiles = listOf(
                    Triple(overview.verifiedWorkers.toString(), stringResource(R.string.admin_stat_verified), StatusTone.Success),
                    Triple(overview.customers.toString(), stringResource(R.string.admin_stat_customers), StatusTone.Brand),
                    Triple(overview.rejectedWorkers.toString(), stringResource(R.string.admin_stat_rejected), StatusTone.Error),
                    Triple(
                        overview.averageRating?.let { "★ " + String.format(Locale.ROOT, "%.1f", it) } ?: "—",
                        if (overview.averageRating != null) {
                            stringResource(R.string.admin_stat_rating)
                        } else {
                            stringResource(R.string.admin_stat_rating_none)
                        },
                        StatusTone.Warning,
                    ),
                ),
            )
        }

        item(key = "activity") {
            StatGrid(
                title = stringResource(R.string.admin_section_activity),
                tiles = listOf(
                    Triple(overview.bookingsToday.toString(), stringResource(R.string.admin_stat_bookings_today), StatusTone.Info),
                    Triple(overview.liveJobs.toString(), stringResource(R.string.admin_stat_live_jobs), StatusTone.Brand),
                    Triple(
                        overview.completedThisWeek.toString(),
                        stringResource(R.string.admin_stat_completed_week),
                        StatusTone.Success,
                    ),
                ),
            )
        }

        item(key = "payments") {
            StatGrid(
                title = stringResource(R.string.admin_section_payments),
                tiles = listOf(
                    Triple(formatRupees(overview.collectedOnline), stringResource(R.string.admin_stat_paid_online), StatusTone.Brand),
                    Triple(formatRupees(overview.collectedCash), stringResource(R.string.admin_stat_paid_cash), StatusTone.Success),
                    Triple(
                        overview.awaitingPayment.toString(),
                        stringResource(R.string.admin_stat_awaiting_payment),
                        if (overview.awaitingPayment > 0) StatusTone.Warning else StatusTone.Neutral,
                    ),
                ),
            )
        }

        item(key = "recent") { RecentDecisions(overview, now, onOpenWorker) }
    }
}

/** The one thing an admin should act on first: the verification queue. */
@Composable
private fun AttentionPanel(pending: Int, onOpenQueue: () -> Unit) {
    val waiting = pending > 0
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (waiting) SahyogTheme.colors.primaryContainer else SahyogTheme.extendedColors.successContainer,
                SahyogTheme.shapes.extraLarge,
            )
            .padding(SahyogSpacing.xl),
    ) {
        val onColor = if (waiting) SahyogTheme.colors.onPrimaryContainer else SahyogTheme.extendedColors.onSuccessContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (waiting) SahyogIcons.Warning else SahyogIcons.Verified,
                contentDescription = null,
                tint = onColor,
            )
            Spacer(Modifier.width(SahyogSpacing.sm))
            Text(
                text = stringResource(if (waiting) R.string.admin_attention_title else R.string.admin_all_clear_title),
                style = SahyogTheme.typography.titleMedium,
                color = onColor,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(SahyogSpacing.md))
        if (waiting) {
            Text(
                text = pending.toString(),
                style = SahyogTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = onColor,
            )
            Text(
                text = pluralStringResource(R.plurals.admin_attention_body, pending, pending),
                style = SahyogTheme.typography.bodyLarge,
                color = onColor,
            )
            Spacer(Modifier.height(SahyogSpacing.lg))
            SahyogButton(
                text = stringResource(R.string.admin_attention_action),
                onClick = onOpenQueue,
                leadingIcon = SahyogIcons.VerifiedOutlined,
            )
        } else {
            Text(
                text = stringResource(R.string.admin_all_clear_body),
                style = SahyogTheme.typography.bodyLarge,
                color = onColor,
            )
        }
    }
}

@Composable
private fun StatGrid(title: String, tiles: List<Triple<String, String, StatusTone>>) {
    Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
        SectionHeader(title)
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                row.forEach { (value, label, tone) -> StatTile(value, label, tone, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentDecisions(overview: AdminOverview, now: Instant, onOpenWorker: (String) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.admin_section_recent))
        Spacer(Modifier.height(SahyogSpacing.sm))
        SahyogCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = SahyogSpacing.lg, vertical = SahyogSpacing.sm),
        ) {
            if (overview.recentDecisions.isEmpty()) {
                Text(
                    text = stringResource(R.string.admin_recent_empty),
                    style = SahyogTheme.typography.bodyMedium,
                    color = SahyogTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = SahyogSpacing.md),
                )
            }
            overview.recentDecisions.forEach { change ->
                StatusChangeRow(change, now, showWorker = true, onClick = { onOpenWorker(change.workerId) })
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
