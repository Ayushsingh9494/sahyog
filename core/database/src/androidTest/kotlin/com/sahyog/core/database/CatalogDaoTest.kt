package com.sahyog.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.dao.likeContains
import com.sahyog.core.database.entity.CategoryEntity
import com.sahyog.core.database.entity.WorkerCategoryCrossRef
import com.sahyog.core.database.entity.WorkerEntity
import com.sahyog.core.database.entity.WorkerServiceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real SQL on a device/emulator against an in-memory database. */
@RunWith(AndroidJUnit4::class)
class CatalogDaoTest {

    private lateinit var database: SahyogDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SahyogDatabase::class.java,
        ).build()
        dao = database.catalogDao()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seed() = dao.replaceCatalog(
        categories = listOf(
            CategoryEntity("electrician", "Electrician", "wiring बिजली", position = 0),
            CategoryEntity("plumber", "Plumber", "tap नल", position = 1),
        ),
        workers = listOf(
            worker("w1", "Ramesh Kumar", rating = 4.8f, locality = "Model Town"),
            worker("w2", "Rajesh Yadav", rating = 4.7f, locality = "Civil Lines"),
            worker("w3", "Sunil Verma", rating = 4.9f, locality = "100% Nagar"),
        ),
        workerCategories = listOf(
            WorkerCategoryCrossRef("w1", "electrician"),
            WorkerCategoryCrossRef("w2", "plumber"),
            WorkerCategoryCrossRef("w3", "electrician"),
        ),
        services = listOf(
            WorkerServiceEntity("w1", 0, "Fan installation", 19_900, "PerVisit"),
            WorkerServiceEntity("w2", 0, "Leak repair", 24_900, "PerVisit"),
        ),
    )

    @Test
    fun workersAreSortedByRatingAndLoadedWithRelations() = runTest {
        seed()
        val workers = dao.observeWorkers().first()
        assertEquals(listOf("w3", "w1", "w2"), workers.map { it.worker.id })
        val ramesh = workers.first { it.worker.id == "w1" }
        assertEquals(listOf("electrician"), ramesh.categories.map { it.id })
        assertEquals(listOf("Fan installation"), ramesh.services.map { it.name })
        assertEquals(listOf("Hindi", "Punjabi"), ramesh.worker.languages)
    }

    @Test
    fun filtersByCategory() = runTest {
        seed()
        assertEquals(listOf("w2"), dao.observeWorkersInCategory("plumber").first().map { it.worker.id })
    }

    @Test
    fun searchMatchesNameAreaCategoryKeywordAndService() = runTest {
        seed()
        suspend fun search(q: String) = dao.searchWorkers(likeContains(q)).first().map { it.worker.id }

        assertEquals(listOf("w1"), search("ramesh")) // name, case-insensitive
        assertEquals(listOf("w2"), search("civil")) // area
        assertEquals(listOf("w3", "w1"), search("बिजली")) // Hindi category keyword
        assertEquals(listOf("w2"), search("plumb")) // category name
        assertEquals(listOf("w2"), search("leak")) // service name
        assertEquals(listOf("w3"), search("100%")) // % matched literally, not as a wildcard
        assertEquals(emptyList<String>(), search("carpenter"))
    }

    @Test
    fun replacingTheCatalogRemovesStaleRowsAndRelations() = runTest {
        seed()
        dao.replaceCatalog(
            categories = listOf(CategoryEntity("plumber", "Plumber", "", position = 0)),
            workers = listOf(worker("w2", "Rajesh Yadav", rating = 4.7f, locality = "Civil Lines")),
            workerCategories = listOf(WorkerCategoryCrossRef("w2", "plumber")),
            services = emptyList(),
        )
        assertEquals(listOf("plumber"), dao.observeCategories().first().map { it.id })
        assertNull(dao.observeWorker("w1").first())
        val rajesh = dao.observeWorker("w2").first()!!
        assertEquals(emptyList<Any>(), rajesh.services)
    }

    private fun worker(id: String, name: String, rating: Float, locality: String) = WorkerEntity(
        id = id,
        name = name,
        rating = rating,
        ratingCount = 10,
        jobsCompleted = 20,
        yearsExperience = 3,
        locality = locality,
        about = "",
        languages = listOf("Hindi", "Punjabi"),
        isAvailable = true,
        startingPricePaise = null,
    )
}
