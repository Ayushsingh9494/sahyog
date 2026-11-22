package com.sahyog.feature.discovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sahyog.core.testing.MainDispatcherRule
import com.sahyog.core.testing.TestLocationRepository
import com.sahyog.core.testing.TestCatalog
import com.sahyog.core.testing.TestCatalogRepository
import com.sahyog.feature.discovery.category.CategoryUiState
import com.sahyog.feature.discovery.category.CategoryViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CategoryViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val location = TestLocationRepository()
    private val catalog = TestCatalogRepository().apply {
        setCatalog(TestCatalog.categories, TestCatalog.workers)
    }

    @Test
    fun `lists the workers in the category`() = runTest {
        CategoryViewModel("electrician", catalog, location).uiState.test {
            val ready = awaitState<CategoryUiState.Ready>()
            assertEquals(TestCatalog.electrician, ready.category)
            assertEquals(listOf("w1", "w2"), ready.workers.map { it.id })
            assertEquals(2, ready.totalCount)
        }
    }

    @Test
    fun `available only filter hides busy workers but keeps the total`() = runTest {
        val viewModel = CategoryViewModel("electrician", catalog, location)
        viewModel.uiState.test {
            awaitState<CategoryUiState.Ready>()
            viewModel.onAvailableOnlyChange(true)
            val filtered = awaitState<CategoryUiState.Ready> { it.filters.availableOnly }
            assertEquals(listOf("w1"), filtered.workers.map { it.id })
            assertEquals(2, filtered.totalCount)
        }
    }

    @Test
    fun `unknown category is not found`() = runTest {
        CategoryViewModel("astrologer", catalog, location).uiState.test {
            awaitState<CategoryUiState.NotFound>()
        }
    }

    @Test
    fun `empty cache stays loading`() = runTest {
        catalog.setCatalog(emptyList(), emptyList())
        CategoryViewModel("electrician", catalog, location).uiState.test {
            assertEquals(CategoryUiState.Loading, awaitItem())
            expectNoEvents()
        }
    }

    private suspend inline fun <reified T : CategoryUiState> ReceiveTurbine<CategoryUiState>.awaitState(
        predicate: (T) -> Boolean = { true },
    ): T {
        while (true) {
            val item = awaitItem()
            if (item is T && predicate(item)) return item
        }
    }
}
