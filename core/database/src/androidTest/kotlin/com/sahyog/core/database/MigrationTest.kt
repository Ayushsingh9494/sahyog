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
    fun migrate2To3KeepsBookings() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                INSERT INTO bookings (id, worker_id, worker_name, service_name, price_paise, price_unit,
                    slot_date, slot_start_hour, address, note, status, start_code, created_at)
                VALUES ('SH-ABC234', 'w1', 'Ramesh Kumar', 'Fan installation', 19900, 'PerVisit',
                    20416, 11, '12 Main Road', NULL, 'Accepted', '4821', 1000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true)

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SahyogDatabase::class.java,
            dbName,
        ).build()
        try {
            runBlocking {
                val booking = database.bookingDao().observeBooking("SH-ABC234").first()
                assertEquals("4821", booking?.startCode)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate1ToLatestKeepsCatalogAndAddsNewTables() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO categories (id, name, keywords, position) VALUES ('plumber', 'Plumber', 'नल', 0)")
            close()
        }

        // Runs every migration up to the latest version and checks the result matches its schema.
        helper.runMigrationsAndValidate(dbName, 3, true)

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SahyogDatabase::class.java,
            dbName,
        ).build()
        try {
            runBlocking {
                assertEquals(listOf("plumber"), database.catalogDao().observeCategories().first().map { it.id })
                assertEquals(emptyList<Any>(), database.bookingDao().observeBookings().first())
                assertEquals(emptyList<Any>(), database.jobDao().observeJobs().first())
            }
        } finally {
            database.close()
        }
    }
}
