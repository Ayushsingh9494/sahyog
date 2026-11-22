package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.model.Paise
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import com.sahyog.core.model.WorkerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory catalog. Search is a simple case-insensitive match on name, area, category and service. */
class TestCatalogRepository : CatalogRepository {
    private val categoriesFlow = MutableStateFlow<List<ServiceCategory>>(emptyList())
    private val workersFlow = MutableStateFlow<List<Worker>>(emptyList())

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshCount = 0
        private set

    val searchQueries = mutableListOf<String>()

    fun setCatalog(categories: List<ServiceCategory>, workers: List<Worker>) {
        categoriesFlow.value = categories
        workersFlow.value = workers
    }

    override val categories: Flow<List<ServiceCategory>> = categoriesFlow

    override fun workers(categoryId: String?): Flow<List<Worker>> = workersFlow.map { workers ->
        if (categoryId == null) workers else workers.filter { w -> w.categories.any { it.id == categoryId } }
    }

    override fun searchWorkers(query: String): Flow<List<Worker>> {
        searchQueries += query
        val q = query.trim()
        return workersFlow.map { workers ->
            if (q.isEmpty()) {
                emptyList()
            } else {
                workers.filter { w ->
                    w.name.contains(q, ignoreCase = true) ||
                        w.locality.contains(q, ignoreCase = true) ||
                        w.categories.any { it.name.contains(q, ignoreCase = true) } ||
                        w.services.any { it.name.contains(q, ignoreCase = true) }
                }
            }
        }
    }

    override fun worker(id: String): Flow<Worker?> = workersFlow.map { workers -> workers.find { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCount++
        return refreshResult
    }
}

/** Sample catalog data for tests. */
object TestCatalog {
    val electrician = ServiceCategory("electrician", "Electrician")
    val plumber = ServiceCategory("plumber", "Plumber")
    val categories = listOf(electrician, plumber)

    fun worker(
        id: String,
        name: String = "Worker $id",
        category: ServiceCategory = electrician,
        rating: Float = 4.5f,
        isAvailable: Boolean = true,
    ) = Worker(
        id = id,
        name = name,
        categories = listOf(category),
        rating = rating,
        ratingCount = 10,
        jobsCompleted = 20,
        yearsExperience = 3,
        locality = "Model Town",
        about = "About $name",
        languages = listOf("Hindi"),
        services = listOf(WorkerService("Repair", Paise.rupees(199), PriceUnit.PerVisit)),
        isAvailable = isAvailable,
    )

    val ramesh = worker("w1", "Ramesh Kumar", electrician, rating = 4.8f)
    val sunil = worker("w2", "Sunil Verma", electrician, rating = 4.6f, isAvailable = false)
    val rajesh = worker("w3", "Rajesh Yadav", plumber, rating = 4.7f)
    val workers = listOf(ramesh, rajesh, sunil)
}
