package com.sahyog.feature.discovery.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.ui.catalog.WorkerCard
import com.sahyog.feature.discovery.R
import com.sahyog.feature.discovery.component.CategoryGrid
import com.sahyog.feature.discovery.component.SectionHeader

@Composable
fun SearchScreen(
    onCategoryClick: (String) -> Unit,
    onWorkerClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    SearchContent(
        query = query,
        results = results,
        onQueryChange = viewModel::onQueryChange,
        onClear = viewModel::onClear,
        onCategoryClick = onCategoryClick,
        onWorkerClick = onWorkerClick,
    )
}

@Composable
internal fun SearchContent(
    query: String,
    results: SearchResultsState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onWorkerClick: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.discovery_search_title)) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = SahyogSpacing.screenGutter,
                end = SahyogSpacing.screenGutter,
                bottom = SahyogSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(SahyogSpacing.md),
        ) {
            item(key = "field") {
                SahyogTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = stringResource(R.string.discovery_search_hint),
                    leadingIcon = SahyogIcons.Search,
                    trailingContent = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = onClear) {
                                Icon(SahyogIcons.Close, stringResource(R.string.discovery_search_clear))
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (results) {
                is SearchResultsState.Idle -> if (results.categories.isNotEmpty()) {
                    item(key = "browse-header") {
                        SectionHeader(stringResource(R.string.discovery_search_browse))
                    }
                    item(key = "browse") { CategoryGrid(results.categories, onCategoryClick) }
                }
                is SearchResultsState.Results -> if (results.workers.isEmpty()) {
                    item(key = "empty") {
                        SahyogEmptyView(
                            title = stringResource(R.string.discovery_search_no_results_title, results.query),
                            message = stringResource(R.string.discovery_search_no_results_message),
                            icon = SahyogIcons.Search,
                        )
                    }
                } else {
                    item(key = "count") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.discovery_search_result_count,
                                results.workers.size,
                                results.workers.size,
                            ),
                            style = SahyogTheme.typography.labelLarge,
                            color = SahyogTheme.colors.onSurfaceVariant,
                            // Screen readers announce the count as results change.
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    items(results.workers, key = { it.id }) { worker ->
                        WorkerCard(
                            worker = worker,
                            onClick = { onWorkerClick(worker.id) },
                            distanceKm = results.distances[worker.id],
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SearchIdlePreview() {
    SahyogTheme {
        SearchContent(
            query = "",
            results = SearchResultsState.Idle(
                listOf(ServiceCategory("electrician", "Electrician"), ServiceCategory("plumber", "Plumber")),
            ),
            onQueryChange = {},
            onClear = {},
            onCategoryClick = {},
            onWorkerClick = {},
        )
    }
}
