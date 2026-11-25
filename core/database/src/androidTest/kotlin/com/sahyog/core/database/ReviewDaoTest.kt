package com.sahyog.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sahyog.core.database.dao.ReviewDao
import com.sahyog.core.database.entity.ReviewEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ReviewDaoTest {

    private lateinit var database: SahyogDatabase
    private lateinit var dao: ReviewDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SahyogDatabase::class.java,
        ).build()
        dao = database.reviewDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun newestFirstAndReplacingOneWorkerLeavesOthers() = runTest {
        dao.upsertAll(listOf(review("r1", "w1", 1_000), review("r2", "w1", 2_000), review("r3", "w2", 1_500)))
        assertEquals(listOf("r2", "r1"), dao.observeReviews("w1").first().map { it.id })

        dao.replaceForWorker("w1", listOf(review("r4", "w1", 3_000)))
        assertEquals(listOf("r4"), dao.observeReviews("w1").first().map { it.id })
        assertEquals(listOf("r3"), dao.observeReviews("w2").first().map { it.id })
    }

    private fun review(id: String, workerId: String, createdAt: Long) = ReviewEntity(
        id = id,
        workerId = workerId,
        authorName = "Asha",
        rating = 5,
        comment = null,
        serviceName = "Fan installation",
        createdAt = Instant.ofEpochMilli(createdAt),
    )
}
