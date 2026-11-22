package com.sahyog.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sahyog.core.database.entity.JobEntity
import kotlinx.coroutines.flow.Flow

/** Offline cache of the signed-in worker's jobs. */
@Dao
interface JobDao {

    /** Soonest visit first. */
    @Query("SELECT * FROM jobs ORDER BY slot_date, slot_start_hour, created_at")
    fun observeJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeJob(id: String): Flow<JobEntity?>

    @Upsert
    suspend fun upsert(job: JobEntity)

    @Upsert
    suspend fun upsertAll(jobs: List<JobEntity>)

    /** Makes the cache match the server's full list. */
    @Transaction
    suspend fun replaceAll(jobs: List<JobEntity>) {
        deleteAll()
        upsertAll(jobs)
    }

    @Query("DELETE FROM jobs")
    suspend fun deleteAll()
}
