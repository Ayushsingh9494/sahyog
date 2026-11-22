package com.sahyog.feature.discovery.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogErrorView
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSize
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.catalog.WorkerCard
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.discovery.R
import com.sahyog.feature.discovery.component.CategoryGrid
import com.sahyog.feature.discovery.component.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onWorkerClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is HomeEvent.RefreshFailed -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()))
            }
        }
    }
    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::onRefresh,
        onRetry = viewModel::onRetry,
        onSearchClick = onSearchClick,
        onCategoryClick = onCategoryClick,
        onWorkerClick = onWorkerClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onWorkerClick: (String) -> Unit,
) {
    Scaffold(
        containerColor = SahyogTheme.colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            HomeUiState.Loading -> SahyogLoadingView(modifier)
            is HomeUiState.Error -> SahyogErrorView(
                title = stringResource(R.string.discovery_home_error_title),
                message = stringResource(uiState.error.messageRes()),
                onRetry = onRetry,
                modifier = modifier,
            )
            HomeUiState.Empty -> PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = modifier) {
                // Scrollable so the pull gesture works on an otherwise static screen.
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        SahyogEmptyView(
                            title = stringResource(R.string.discovery_home_empty_title),
                            message = stringResource(R.string.discovery_home_empty_message),
                            icon = SahyogIcons.Service,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
            }
            is HomeUiState.Ready -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
            ) {
                HomeList(uiState, onSearchClick, onCategoryClick, onWorkerClick)
            }
        }
    }
}

@Composable
private fun HomeList(
    uiState: HomeUiState.Ready,
    onSearchClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onWorkerClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.xl,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
    ) {
        item(key = "greeting") {
            Column {
                Text(
                    text = uiState.firstName?.let { stringResource(R.string.discovery_home_greeting, it) }
                        ?: stringResource(R.string.discovery_home_greeting_no_name),
                    style = SahyogTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.discovery_home_subtitle),
                    style = SahyogTheme.typography.bodyLarge,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
        }
        item(key = "search") { SearchEntry(onClick = onSearchClick) }
        item(key = "services-header") {
            SectionHeader(
                text = stringResource(R.string.discovery_home_services),
                modifier = Modifier.padding(top = SahyogSpacing.sm),
            )
        }
        item(key = "services") { CategoryGrid(uiState.categories, onCategoryClick) }
        if (uiState.topWorkers.isNotEmpty()) {
            item(key = "top-header") {
                SectionHeader(
                    text = stringResource(R.string.discovery_home_top_rated),
                    modifier = Modifier.padding(top = SahyogSpacing.sm),
                )
            }
            items(uiState.topWorkers, key = { it.id }) { worker ->
                WorkerCard(worker = worker, onClick = { onWorkerClick(worker.id) })
            }
        }
    }
}

/** Looks like a search field but opens the Search tab, where typing happens. */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        shape = SahyogTheme.shapes.extraLarge,
        color = SahyogTheme.colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SearchEntryHeight)
            .clip(SahyogTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = SahyogSpacing.lg, vertical = SahyogSpacing.lg),
        ) {
            Icon(
                SahyogIcons.Search,
                contentDescription = null,
                tint = SahyogTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(end = SahyogSpacing.xs),
            )
            Spacer(Modifier.width(SahyogSpacing.sm))
            Text(
                text = stringResource(R.string.discovery_search_hint),
                style = SahyogTheme.typography.bodyLarge,
                color = SahyogTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private val SearchEntryHeight = SahyogSize.minTouchTarget

@Preview
@Composable
private fun HomeContentPreview() {
    SahyogTheme {
        HomeContent(
            uiState = HomeUiState.Ready(
                firstName = "Asha",
                categories = listOf(
                    ServiceCategory("electrician", "Electrician"),
                    ServiceCategory("plumber", "Plumber"),
                    ServiceCategory("carpenter", "Carpenter"),
                    ServiceCategory("painter", "Painter"),
                    ServiceCategory("cleaning", "Cleaning"),
                ),
                topWorkers = emptyList(),
                isRefreshing = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onRetry = {},
            onSearchClick = {},
            onCategoryClick = {},
            onWorkerClick = {},
        )
    }
}
