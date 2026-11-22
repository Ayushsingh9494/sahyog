package com.sahyog.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.sahyog.core.database.entity.CategoryEntity
import com.sahyog.core.database.entity.PopulatedWorker
import com.sahyog.core.database.entity.WorkerCategoryCrossRef
import com.sahyog.core.database.entity.WorkerEntity
import com.sahyog.core.database.entity.WorkerServiceEntity
import kotlinx.coroutines.flow.Flow

/** Offline cache of the service catalog: categories and the workers customers can book. */
@Dao
interface CatalogDao {

    @Query("SELECT * FROM categories ORDER BY position")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Transaction
    @Query("SELECT * FROM workers ORDER BY rating DESC, rating_count DESC, name")
    fun observeWorkers(): Flow<List<PopulatedWorker>>

    @Transaction
    @Query(
        """
        SELECT * FROM workers
        WHERE id IN (SELECT worker_id FROM worker_categories WHERE category_id = :categoryId)
        ORDER BY rating DESC, rating_count DESC, name
        """,
    )
    fun observeWorkersInCategory(categoryId: String): Flow<List<PopulatedWorker>>

    /** [pattern] is a LIKE pattern with `\` as escape character; build it with [likeContains]. */
    @Transaction
    @Query(
        """
        SELECT * FROM workers
        WHERE name LIKE :pattern ESCAPE '\'
           OR locality LIKE :pattern ESCAPE '\'
           OR id IN (
               SELECT wc.worker_id FROM worker_categories wc
               JOIN categories c ON c.id = wc.category_id
               WHERE c.name LIKE :pattern ESCAPE '\' OR c.keywords LIKE :pattern ESCAPE '\'
           )
           OR id IN (SELECT worker_id FROM worker_services WHERE name LIKE :pattern ESCAPE '\')
        ORDER BY rating DESC, rating_count DESC, name
        """,
    )
    fun searchWorkers(pattern: String): Flow<List<PopulatedWorker>>

    @Transaction
    @Query("SELECT * FROM workers WHERE id = :id")
    fun observeWorker(id: String): Flow<PopulatedWorker?>

    /**
     * Replaces the whole catalog atomically, so observers never see a half-written state.
     * Deleting workers and categories cascades to the join and service tables.
     */
    @Transaction
    suspend fun replaceCatalog(
        categories: List<CategoryEntity>,
        workers: List<WorkerEntity>,
        workerCategories: List<WorkerCategoryCrossRef>,
        services: List<WorkerServiceEntity>,
    ) {
        deleteWorkers()
        deleteCategories()
        insertCategories(categories)
        insertWorkers(workers)
        insertWorkerCategories(workerCategories)
        insertServices(services)
    }

    @Query("DELETE FROM workers")
    suspend fun deleteWorkers()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Insert
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert
    suspend fun insertWorkers(workers: List<WorkerEntity>)

    @Insert
    suspend fun insertWorkerCategories(refs: List<WorkerCategoryCrossRef>)

    @Insert
    suspend fun insertServices(services: List<WorkerServiceEntity>)
}

/** A LIKE pattern matching [text] anywhere, with `%`, `_` and `\` escaped so they match literally. */
fun likeContains(text: String): String {
    val escaped = text.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
