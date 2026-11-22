package com.sahyog.feature.discovery

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.feature.discovery.search.SearchResultsState
import com.sahyog.feature.discovery.search.SearchViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val catalog = TestCatalogRepository().apply {
        setCatalog(TestCatalog.categories, TestCatalog.workers)
    }
    private val viewModel by lazy { SearchViewModel(SavedStateHandle(), catalog) }

    @Test
    fun `empty query suggests categories`() = runTest {
        viewModel.results.test {
            val idle = awaitState<SearchResultsState.Idle> { it.categories.isNotEmpty() }
            assertEquals(TestCatalog.categories, idle.categories)
        }
    }

    @Test
    fun `typing searches after a pause and only for the final query`() = runTest {
        viewModel.results.test {
            awaitState<SearchResultsState.Idle> { it.categories.isNotEmpty() }
            viewModel.onQueryChange("p")
            viewModel.onQueryChange("pl")
            viewModel.onQueryChange("plumber ")
            val results = awaitState<SearchResultsState.Results>()
            assertEquals("plumber", results.query)
            assertEquals(listOf("w3"), results.workers.map { it.id })
        }
        // Debounced: the intermediate "p" and "pl" never reached the repository.
        assertEquals(listOf("plumber"), catalog.searchQueries)
    }

    @Test
    fun `query updates immediately for the text field`() {
        viewModel.onQueryChange("Ram")
        assertEquals("Ram", viewModel.query.value)
    }

    @Test
    fun `no matches gives empty results with the query`() = runTest {
        viewModel.results.test {
            viewModel.onQueryChange("astrologer")
            val results = awaitState<SearchResultsState.Results>()
            assertEquals("astrologer", results.query)
            assertEquals(emptyList<Any>(), results.workers)
        }
    }

    @Test
    fun `clearing returns to suggestions`() = runTest {
        viewModel.results.test {
            viewModel.onQueryChange("ramesh")
            awaitState<SearchResultsState.Results>()
            viewModel.onClear()
            awaitState<SearchResultsState.Idle>()
        }
        assertEquals("", viewModel.query.value)
    }

    @Test
    fun `restores the query after process death`() = runTest {
        val restored = SearchViewModel(SavedStateHandle(mapOf("query" to "rajesh")), catalog)
        assertEquals("rajesh", restored.query.value)
        restored.results.test {
            assertEquals(listOf("w3"), awaitState<SearchResultsState.Results>().workers.map { it.id })
        }
    }

    private suspend inline fun <reified T : SearchResultsState> ReceiveTurbine<SearchResultsState>.awaitState(
        predicate: (T) -> Boolean = { true },
    ): T {
        while (true) {
            val item = awaitItem()
            if (item is T && predicate(item)) return item
        }
    }
}
