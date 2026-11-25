package com.sahyog.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sahyog.core.database.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

/** Offline cache of workers' public reviews. */
@Dao
interface ReviewDao {

    /** Newest first. */
    @Query("SELECT * FROM worker_reviews WHERE worker_id = :workerId ORDER BY created_at DESC")
    fun observeReviews(workerId: String): Flow<List<ReviewEntity>>

    /** Makes the cache of one worker's reviews match what the server returned. */
    @Transaction
    suspend fun replaceForWorker(workerId: String, reviews: List<ReviewEntity>) {
        deleteForWorker(workerId)
        upsertAll(reviews)
    }

    @Upsert
    suspend fun upsertAll(reviews: List<ReviewEntity>)

    @Query("DELETE FROM worker_reviews WHERE worker_id = :workerId")
    suspend fun deleteForWorker(workerId: String)
}
