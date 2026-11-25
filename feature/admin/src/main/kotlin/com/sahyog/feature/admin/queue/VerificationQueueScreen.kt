package com.sahyog.feature.admin.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.VerificationRequest
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.admin.R
import com.sahyog.feature.admin.component.PersonCard
import com.sahyog.feature.admin.component.categoriesLabel
import com.sahyog.feature.admin.component.documentLabel
import com.sahyog.feature.admin.component.relativeTime
import java.time.Duration
import java.time.Instant

@Composable
fun VerificationQueueScreen(
    viewModel: VerificationQueueViewModel,
    onOpenWorker: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VerificationQueueContent(uiState, viewModel::onRefresh, onOpenWorker, snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerificationQueueContent(
    uiState: QueueUiState,
    onRefresh: () -> Unit,
    onOpenWorker: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.admin_queue_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            QueueUiState.Loading -> SahyogLoadingView(modifier)
            is QueueUiState.Error -> SahyogErrorView(
                title = stringResource(R.string.admin_error_title),
                message = stringResource(uiState.error.messageRes()),
                onRetry = onRefresh,
                modifier = modifier,
            )
            is QueueUiState.Ready -> PullToRefreshBox(uiState.isRefreshing, onRefresh, modifier) {
                if (uiState.requests.isEmpty()) {
                    SahyogEmptyView(
                        title = stringResource(R.string.admin_queue_empty_title),
                        message = stringResource(R.string.admin_queue_empty_body),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Queue(uiState.requests, uiState.now, onOpenWorker)
                }
            }
        }
    }
}

@Composable
private fun Queue(requests: List<VerificationRequest>, now: Instant, onOpenWorker: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
    ) {
        item(key = "count") {
            Text(
                text = pluralStringResource(R.plurals.admin_queue_count, requests.size, requests.size),
                style = SahyogTheme.typography.bodyMedium,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
        items(requests, key = { it.workerId }) { request ->
            // Anyone waiting more than a day is highlighted: the cooperative should answer within one.
            val overdue = Duration.between(request.submittedAt, now) > OVERDUE
            PersonCard(
                name = request.name,
                subtitle = listOfNotNull(categoriesLabel(request.categoryIds).ifBlank { null }, request.locality)
                    .joinToString(" · "),
                onClick = { onOpenWorker(request.workerId) },
                trailing = { SahyogStatusBadge(documentLabel(request.documentType), StatusTone.Neutral) },
                footer = {
                    Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                        SahyogStatusBadge(
                            text = stringResource(R.string.admin_queue_waiting, relativeTime(request.submittedAt, now)),
                            tone = if (overdue) StatusTone.Error else StatusTone.Warning,
                            icon = SahyogIcons.Bookings,
                        )
                        if (request.isResubmission) {
                            SahyogStatusBadge(stringResource(R.string.admin_queue_resubmitted), StatusTone.Info)
                        }
                    }
                },
            )
        }
    }
}

private val OVERDUE: Duration = Duration.ofHours(24)
