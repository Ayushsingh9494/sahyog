package com.sahyog.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrading must keep existing data. Uses the schemas exported to core/database/schemas. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SahyogDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsCatalogAndAddsBookings() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO categories (id, name, keywords, position) VALUES ('plumber', 'Plumber', 'नल', 0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true)

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SahyogDatabase::class.java,
            dbName,
        ).build()
        try {
            runBlocking {
                assertEquals(listOf("plumber"), database.catalogDao().observeCategories().first().map { it.id })
                assertEquals(emptyList<Any>(), database.bookingDao().observeBookings().first())
            }
        } finally {
            database.close()
        }
    }
}
