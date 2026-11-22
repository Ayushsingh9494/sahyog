package com.sahyog.feature.discovery.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogFilterChip
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.ui.catalog.WorkerCard
import com.sahyog.core.ui.catalog.categoryLabel
import com.sahyog.feature.discovery.R

@Composable
fun CategoryScreen(
    viewModel: CategoryViewModel,
    onNavigateBack: () -> Unit,
    onWorkerClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAvailableOnlyChange = viewModel::onAvailableOnlyChange,
        onWorkerClick = onWorkerClick,
    )
}

@Composable
internal fun CategoryContent(
    uiState: CategoryUiState,
    onNavigateBack: () -> Unit,
    onAvailableOnlyChange: (Boolean) -> Unit,
    onWorkerClick: (String) -> Unit,
) {
    val title = (uiState as? CategoryUiState.Ready)?.let { categoryLabel(it.category) }.orEmpty()
    Scaffold(
        topBar = { SahyogTopAppBar(title = title, onNavigateBack = onNavigateBack) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            CategoryUiState.Loading -> SahyogLoadingView(modifier)
            CategoryUiState.NotFound -> SahyogEmptyView(
                title = stringResource(R.string.discovery_not_found_title),
                message = stringResource(R.string.discovery_not_found_message),
                modifier = modifier,
            )
            is CategoryUiState.Ready -> CategoryList(uiState, onAvailableOnlyChange, onWorkerClick, modifier)
        }
    }
}

@Composable
private fun CategoryList(
    uiState: CategoryUiState.Ready,
    onAvailableOnlyChange: (Boolean) -> Unit,
    onWorkerClick: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
    ) {
        if (uiState.totalCount == 0) {
            item {
                SahyogEmptyView(
                    title = stringResource(R.string.discovery_category_empty_title),
                    message = stringResource(R.string.discovery_category_empty_message),
                    icon = SahyogIcons.Service,
                )
            }
            return@LazyColumn
        }
        item(key = "filters") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SahyogFilterChip(
                    label = stringResource(R.string.discovery_category_available_now),
                    selected = uiState.availableOnly,
                    onClick = { onAvailableOnlyChange(!uiState.availableOnly) },
                    leadingIcon = if (uiState.availableOnly) SahyogIcons.Verified else null,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = pluralStringResource(
                        R.plurals.discovery_category_worker_count,
                        uiState.workers.size,
                        uiState.workers.size,
                    ),
                    style = SahyogTheme.typography.labelLarge,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
        }
        if (uiState.workers.isEmpty()) {
            item(key = "none-available") {
                SahyogEmptyView(
                    title = stringResource(R.string.discovery_category_none_available_title),
                    message = stringResource(R.string.discovery_category_none_available_message),
                    icon = SahyogIcons.Info,
                    actionLabel = stringResource(R.string.discovery_category_show_all),
                    onAction = { onAvailableOnlyChange(false) },
                )
            }
        }
        items(uiState.workers, key = { it.id }) { worker ->
            WorkerCard(worker = worker, onClick = { onWorkerClick(worker.id) })
        }
    }
}
