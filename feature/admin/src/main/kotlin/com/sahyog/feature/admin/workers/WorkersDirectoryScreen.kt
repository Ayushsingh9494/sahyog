package com.sahyog.feature.admin.workers

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogFilterChip
import com.sahyog.core.designsystem.component.SahyogRatingSummary
import com.sahyog.core.designsystem.component.SahyogStatusBadge
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.component.StatusTone
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.AdminWorker
import com.sahyog.core.model.WorkerVerificationStatus
import com.sahyog.core.ui.formatIndianPhone
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.admin.R
import com.sahyog.feature.admin.component.PersonCard
import com.sahyog.feature.admin.component.StatusBadge
import com.sahyog.feature.admin.component.categoriesLabel
import com.sahyog.feature.admin.component.labelRes

@Composable
fun WorkersDirectoryScreen(
    viewModel: WorkersDirectoryViewModel,
    onOpenWorker: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkersDirectoryContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onStatusSelect = viewModel::onStatusSelect,
        onRetry = viewModel::onRefresh,
        onOpenWorker = onOpenWorker,
        snackbarHostState = snackbarHostState,
    )
}

/** Filters in the order an admin thinks about them: who needs action first. */
private val FILTERS: List<WorkerVerificationStatus?> = listOf(
    null,
    WorkerVerificationStatus.Pending,
    WorkerVerificationStatus.Verified,
    WorkerVerificationStatus.Rejected,
    WorkerVerificationStatus.NotSubmitted,
)

@Composable
internal fun WorkersDirectoryContent(
    uiState: WorkersUiState,
    onQueryChange: (String) -> Unit,
    onStatusSelect: (WorkerVerificationStatus?) -> Unit,
    onRetry: () -> Unit,
    onOpenWorker: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.admin_workers_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SahyogTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.admin_search_hint),
                leadingIcon = SahyogIcons.Search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SahyogSpacing.screenGutter),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
            ) {
                items(FILTERS) { status ->
                    SahyogFilterChip(
                        label = stringResource(status?.labelRes() ?: R.string.admin_status_all),
                        selected = uiState.status == status,
                        onClick = { onStatusSelect(status) },
                    )
                }
            }
            // A thin bar rather than a spinner: the previous results stay visible while searching.
            if (uiState.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                // Same height as the bar, so the list doesn't jump when loading ends.
                Spacer(Modifier.height(4.dp))
            }
            when {
                uiState.error != null && uiState.workers.isEmpty() -> SahyogErrorView(
                    title = stringResource(R.string.admin_error_title),
                    message = stringResource(uiState.error.messageRes()),
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
                !uiState.isLoading && uiState.workers.isEmpty() -> SahyogEmptyView(
                    title = stringResource(R.string.admin_workers_empty),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = SahyogSpacing.screenGutter,
                        end = SahyogSpacing.screenGutter,
                        top = SahyogSpacing.sm,
                        bottom = SahyogSpacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
                ) {
                    items(uiState.workers, key = { it.id }) { worker -> WorkerRow(worker, onOpenWorker) }
                }
            }
        }
    }
}

@Composable
private fun WorkerRow(worker: AdminWorker, onOpenWorker: (String) -> Unit) {
    PersonCard(
        name = worker.name,
        subtitle = formatIndianPhone(worker.phoneNumber),
        onClick = { onOpenWorker(worker.id) },
        trailing = { StatusBadge(worker.status) },
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(categoriesLabel(worker.categoryIds).ifBlank { null }, worker.locality)
                        .joinToString(" · "),
                    style = SahyogTheme.typography.bodySmall,
                    color = SahyogTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(SahyogSpacing.sm))
                if (worker.ratingCount > 0) {
                    SahyogRatingSummary(rating = worker.rating, reviewCount = worker.ratingCount)
                } else if (worker.status == WorkerVerificationStatus.Verified) {
                    SahyogStatusBadge(stringResource(R.string.admin_new), StatusTone.Brand)
                }
                if (worker.jobsCompleted > 0) {
                    Spacer(Modifier.width(SahyogSpacing.sm))
                    Text(
                        text = pluralStringResource(R.plurals.admin_workers_jobs, worker.jobsCompleted, worker.jobsCompleted),
                        style = SahyogTheme.typography.labelMedium,
                    )
                }
            }
        },
    )
}
