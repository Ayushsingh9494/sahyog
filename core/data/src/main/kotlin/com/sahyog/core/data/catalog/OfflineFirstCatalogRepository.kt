package com.sahyog.core.data.catalog

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.CatalogRepository
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.dao.likeContains
import com.sahyog.core.database.entity.CategoryEntity
import com.sahyog.core.database.entity.PopulatedWorker
import com.sahyog.core.database.entity.WorkerCategoryCrossRef
import com.sahyog.core.database.entity.WorkerEntity
import com.sahyog.core.database.entity.WorkerServiceEntity
import com.sahyog.core.database.entity.asExternalModel
import com.sahyog.core.model.ServiceCategory
import com.sahyog.core.model.Worker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Offline-first catalog: screens only ever read Room; [refresh] downloads the catalog
 * and replaces the cached copy in one transaction. If a refresh fails, the cached data
 * stays on screen and the caller decides how loudly to report the error.
 */
@Singleton
internal class OfflineFirstCatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val network: CatalogNetworkDataSource,
) : CatalogRepository {

    // Home, Category and Search may all ask to refresh at once; one download is enough.
    private val refreshMutex = Mutex()

    override val categories: Flow<List<ServiceCategory>> = dao.observeCategories()
        .map { entities -> entities.map { it.asExternalModel() } }
        .distinctUntilChanged()

    override fun workers(categoryId: String?): Flow<List<Worker>> =
        (if (categoryId == null) dao.observeWorkers() else dao.observeWorkersInCategory(categoryId))
            .asWorkers()

    override fun searchWorkers(query: String): Flow<List<Worker>> =
        if (query.isBlank()) flowOf(emptyList()) else dao.searchWorkers(likeContains(query)).asWorkers()

    override fun worker(id: String): Flow<Worker?> = dao.observeWorker(id)
        .map { it?.asExternalModel() }
        .distinctUntilChanged()

    override suspend fun refresh(): AppResult<Unit> {
        // A refresh that was waiting for one in progress has nothing new to add.
        if (refreshMutex.isLocked) return refreshMutex.withLock { AppResult.Success(Unit) }
        return refreshMutex.withLock {
            try {
                val catalog = network.fetchCatalog()
                writeToCache(catalog)
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppResult.Failure(AppError.Network)
            } catch (e: Exception) {
                AppResult.Failure(AppError.Unknown(e))
            }
        }
    }

    private suspend fun writeToCache(catalog: NetworkCatalog) {
        val knownCategoryIds = catalog.categories.map { it.id }.toSet()
        dao.replaceCatalog(
            categories = catalog.categories.mapIndexed { index, category ->
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    keywords = category.keywords.joinToString(" "),
                    position = index,
                )
            },
            workers = catalog.workers.map { worker ->
                WorkerEntity(
                    id = worker.id,
                    name = worker.name,
                    rating = worker.rating,
                    ratingCount = worker.ratingCount,
                    jobsCompleted = worker.jobsCompleted,
                    yearsExperience = worker.yearsExperience,
                    locality = worker.locality,
                    about = worker.about,
                    languages = worker.languages,
                    isAvailable = worker.isAvailable,
                    startingPricePaise = worker.services.minOfOrNull { it.pricePaise },
                    latitude = worker.latitude,
                    longitude = worker.longitude,
                )
            },
            // Skip links to categories the response didn't include, so a partial
            // server response can't violate the foreign key and abort the whole refresh.
            workerCategories = catalog.workers.flatMap { worker ->
                worker.categoryIds.distinct()
                    .filter { it in knownCategoryIds }
                    .map { WorkerCategoryCrossRef(workerId = worker.id, categoryId = it) }
            },
            services = catalog.workers.flatMap { worker ->
                worker.services.mapIndexed { index, service ->
                    WorkerServiceEntity(
                        workerId = worker.id,
                        position = index,
                        name = service.name,
                        pricePaise = service.pricePaise,
                        unit = service.unit,
                    )
                }
            },
        )
    }

    private fun Flow<List<PopulatedWorker>>.asWorkers(): Flow<List<Worker>> =
        map { workers -> workers.map { it.asExternalModel() } }.distinctUntilChanged()
}
