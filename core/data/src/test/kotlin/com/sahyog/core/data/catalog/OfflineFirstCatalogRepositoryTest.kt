package com.sahyog.core.data.catalog

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.dao.likeContains
import com.sahyog.core.database.entity.CategoryEntity
import com.sahyog.core.database.entity.PopulatedWorker
import com.sahyog.core.database.entity.WorkerCategoryCrossRef
import com.sahyog.core.database.entity.WorkerEntity
import com.sahyog.core.database.entity.WorkerServiceEntity
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OfflineFirstCatalogRepositoryTest {

    private val dao = FakeCatalogDao()
    private val network = FakeNetwork()
    private val repository = OfflineFirstCatalogRepository(dao, network)

    @Test
    fun `refresh stores the catalog and maps it to models`() = runTest {
        assertEquals(AppResult.Success(Unit), repository.refresh())

        assertEquals(listOf("electrician", "plumber"), repository.categories.first().map { it.id })
        val workers = repository.workers().first()
        assertEquals(listOf("w1", "w2"), workers.map { it.id })
        val ramesh = workers.first()
        assertEquals(listOf("Electrician", "Plumber"), ramesh.categories.map { it.name })
        assertEquals(listOf("Fan", "Wiring"), ramesh.services.map { it.name })
        assertEquals(PriceUnit.PerHour, ramesh.services[1].unit)
        assertEquals(Paise.rupees(199), ramesh.startingPrice)
    }

    @Test
    fun `workers can be filtered by category`() = runTest {
        repository.refresh()
        assertEquals(listOf("w1"), repository.workers("plumber").first().map { it.id })
    }

    @Test
    fun `links to categories missing from the response are dropped`() = runTest {
        network.catalog = network.catalog.copy(
            workers = network.catalog.workers.map { it.copy(categoryIds = it.categoryIds + "retired") },
        )
        assertEquals(AppResult.Success(Unit), repository.refresh())
        assertTrue(dao.crossRefs.value.none { it.categoryId == "retired" })
    }

    @Test
    fun `network failure keeps the cached catalog`() = runTest {
        repository.refresh()
        network.failure = IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), repository.refresh())
        assertEquals(2, repository.workers().first().size)
    }

    @Test
    fun `unexpected failures map to unknown`() = runTest {
        network.failure = IllegalStateException("bad payload")
        val result = repository.refresh()
        assertTrue(result is AppResult.Failure && result.error is AppError.Unknown)
    }

    @Test
    fun `blank search returns nothing without querying`() = runTest {
        repository.refresh()
        assertEquals(emptyList<Any>(), repository.searchWorkers("  ").first())
        assertTrue(dao.searchPatterns.isEmpty())
    }

    @Test
    fun `search passes an escaped pattern`() = runTest {
        repository.searchWorkers("50% off_").first()
        assertEquals(listOf("%50\\% off\\_%"), dao.searchPatterns)
    }

    @Test
    fun `likeContains trims and escapes backslashes`() {
        assertEquals("%a\\\\b%", likeContains("  a\\b "))
    }

    private class FakeNetwork : CatalogNetworkDataSource {
        var failure: Exception? = null
        var catalog = NetworkCatalog(
            categories = listOf(
                NetworkCategory("electrician", "Electrician", listOf("बिजली")),
                NetworkCategory("plumber", "Plumber", emptyList()),
            ),
            workers = listOf(
                NetworkWorker(
                    id = "w1", name = "Ramesh", categoryIds = listOf("plumber", "electrician"),
                    rating = 4.8f, ratingCount = 10, jobsCompleted = 20, yearsExperience = 5,
                    locality = "Model Town", about = "", languages = listOf("Hindi"),
                    services = listOf(
                        NetworkWorkerService("Fan", 19_900, "PerVisit"),
                        NetworkWorkerService("Wiring", 29_900, "PerHour"),
                    ),
                    isAvailable = true,
                ),
                NetworkWorker(
                    id = "w2", name = "Sunil", categoryIds = listOf("electrician"),
                    rating = 4.5f, ratingCount = 3, jobsCompleted = 4, yearsExperience = 1,
                    locality = "Civil Lines", about = "", languages = emptyList(),
                    services = emptyList(), isAvailable = false,
                ),
            ),
        )

        override suspend fun fetchCatalog(): NetworkCatalog {
            failure?.let { throw it }
            return catalog
        }
    }

    /** In-memory stand-in for Room. SQL behaviour itself is covered by CatalogDaoTest on a device. */
    private class FakeCatalogDao : CatalogDao {
        val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
        val workers = MutableStateFlow<List<WorkerEntity>>(emptyList())
        val crossRefs = MutableStateFlow<List<WorkerCategoryCrossRef>>(emptyList())
        val services = MutableStateFlow<List<WorkerServiceEntity>>(emptyList())
        val searchPatterns = mutableListOf<String>()

        private fun populated(filter: (WorkerEntity) -> Boolean): Flow<List<PopulatedWorker>> =
            workers.map { list ->
                list.filter(filter)
                    .sortedByDescending { it.rating }
                    .map { worker ->
                        val categoryIds = crossRefs.value.filter { it.workerId == worker.id }.map { it.categoryId }
                        PopulatedWorker(
                            worker = worker,
                            // Reversed on purpose: the mapper must restore display order.
                            categories = categories.value.filter { it.id in categoryIds }.reversed(),
                            services = services.value.filter { it.workerId == worker.id }.reversed(),
                        )
                    }
            }

        override fun observeCategories() = categories.map { list -> list.sortedBy { it.position } }
        override fun observeWorkers() = populated { true }
        override fun observeWorkersInCategory(categoryId: String) = populated { worker ->
            crossRefs.value.any { it.workerId == worker.id && it.categoryId == categoryId }
        }
        override fun searchWorkers(pattern: String): Flow<List<PopulatedWorker>> {
            searchPatterns += pattern
            return populated { true }
        }
        override fun observeWorker(id: String) = populated { it.id == id }.map { it.firstOrNull() }

        override suspend fun replaceCatalog(
            categories: List<CategoryEntity>,
            workers: List<WorkerEntity>,
            workerCategories: List<WorkerCategoryCrossRef>,
            services: List<WorkerServiceEntity>,
        ) {
            this.categories.value = categories
            this.crossRefs.value = workerCategories
            this.services.value = services
            this.workers.value = workers
        }

        override suspend fun deleteWorkers() = error("not used")
        override suspend fun deleteCategories() = error("not used")
        override suspend fun insertCategories(categories: List<CategoryEntity>) = error("not used")
        override suspend fun insertWorkers(workers: List<WorkerEntity>) = error("not used")
        override suspend fun insertWorkerCategories(refs: List<WorkerCategoryCrossRef>) = error("not used")
        override suspend fun insertServices(services: List<WorkerServiceEntity>) = error("not used")
    }
}
