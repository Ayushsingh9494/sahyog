package com.sahyog.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.dao.JobDao
import com.sahyog.core.database.entity.BookingEntity
import com.sahyog.core.database.entity.CategoryEntity
import com.sahyog.core.database.entity.JobEntity
import com.sahyog.core.database.entity.WorkerCategoryCrossRef
import com.sahyog.core.database.entity.WorkerEntity
import com.sahyog.core.database.entity.WorkerServiceEntity

/**
 * The app's local database. It is a cache of server data: everything in it can be
 * downloaded again, so it never holds the only copy of anything.
 *
 * Bump [version] with every schema change and add a migration; schemas are exported
 * to `core/database/schemas`.
 */
@Database(
    entities = [
        CategoryEntity::class,
        WorkerEntity::class,
        WorkerCategoryCrossRef::class,
        WorkerServiceEntity::class,
        BookingEntity::class,
        JobEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: bookings table. Purely additive, so Room generates the migration.
        AutoMigration(from = 1, to = 2),
        // v3: jobs table (worker side).
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class SahyogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun bookingDao(): BookingDao

    abstract fun jobDao(): JobDao
}
