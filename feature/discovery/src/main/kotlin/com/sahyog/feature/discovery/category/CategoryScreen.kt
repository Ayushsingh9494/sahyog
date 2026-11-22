package com.sahyog.feature.discovery.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sahyog.core.model.GeoPoint
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
        actions = CategoryActions(
            onAvailableOnlyChange = viewModel::onAvailableOnlyChange,
            onNearestFirstChange = viewModel::onNearestFirstChange,
            onRadiusChange = viewModel::onRadiusChange,
            onClearFilters = viewModel::onClearFilters,
            onWorkerClick = onWorkerClick,
        ),
    )
}

internal data class CategoryActions(
    val onAvailableOnlyChange: (Boolean) -> Unit,
    val onNearestFirstChange: (Boolean) -> Unit,
    val onRadiusChange: (Int?) -> Unit,
    val onClearFilters: () -> Unit,
    val onWorkerClick: (String) -> Unit,
)

@Composable
internal fun CategoryContent(
    uiState: CategoryUiState,
    onNavigateBack: () -> Unit,
    actions: CategoryActions,
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
            is CategoryUiState.Ready -> CategoryList(uiState, actions, modifier)
        }
    }
}

@Composable
private fun CategoryList(
    uiState: CategoryUiState.Ready,
    actions: CategoryActions,
    modifier: Modifier,
) {
    val filters = uiState.filters
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
            Column(verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xs)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                    item {
                        SahyogFilterChip(
                            label = stringResource(R.string.discovery_category_available_now),
                            selected = filters.availableOnly,
                            onClick = { actions.onAvailableOnlyChange(!filters.availableOnly) },
                            leadingIcon = if (filters.availableOnly) SahyogIcons.Verified else null,
                        )
                    }
                    // Distance options only make sense once we know where the customer is.
                    if (uiState.hasLocation) {
                        item {
                            SahyogFilterChip(
                                label = stringResource(R.string.discovery_sort_nearest),
                                selected = filters.nearestFirst,
                                onClick = { actions.onNearestFirstChange(!filters.nearestFirst) },
                                leadingIcon = SahyogIcons.Location,
                            )
                        }
                        item {
                            SahyogFilterChip(
                                label = stringResource(R.string.discovery_radius_any),
                                selected = filters.radiusKm == null,
                                onClick = { actions.onRadiusChange(null) },
                            )
                        }
                        items(GeoPoint.RADIUS_OPTIONS_KM) { km ->
                            SahyogFilterChip(
                                label = stringResource(R.string.discovery_radius_km, km),
                                selected = filters.radiusKm == km,
                                onClick = { actions.onRadiusChange(km) },
                            )
                        }
                    }
                }
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
                // Say *why* the list is empty: too far away, or nobody free right now.
                val radius = filters.radiusKm.takeIf { uiState.hasLocation }
                SahyogEmptyView(
                    title = if (radius != null) {
                        stringResource(R.string.discovery_category_none_nearby_title, radius)
                    } else {
                        stringResource(R.string.discovery_category_none_available_title)
                    },
                    message = if (radius != null) {
                        stringResource(R.string.discovery_category_none_nearby_message)
                    } else {
                        stringResource(R.string.discovery_category_none_available_message)
                    },
                    icon = SahyogIcons.Info,
                    actionLabel = stringResource(R.string.discovery_category_show_all),
                    onAction = actions.onClearFilters,
                )
            }
        }
        items(uiState.workers, key = { it.id }) { worker ->
            WorkerCard(
                worker = worker,
                onClick = { actions.onWorkerClick(worker.id) },
                distanceKm = uiState.distances[worker.id],
            )
        }
    }
}
