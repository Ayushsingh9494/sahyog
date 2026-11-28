package com.sahyog.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sahyog.core.database.dao.BookingDao
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.dao.JobDao
import com.sahyog.core.database.dao.ReviewDao
import com.sahyog.core.database.entity.BookingEntity
import com.sahyog.core.database.entity.CategoryEntity
import com.sahyog.core.database.entity.JobEntity
import com.sahyog.core.database.entity.ReviewEntity
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
        ReviewEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        // v2: bookings table. Purely additive, so Room generates the migration.
        AutoMigration(from = 1, to = 2),
        // v3: jobs table (worker side).
        AutoMigration(from = 2, to = 3),
        // v4: worker coordinates (nullable columns).
        AutoMigration(from = 3, to = 4),
        // v5: reviews (nullable review columns on bookings and jobs, worker_reviews table).
        AutoMigration(from = 4, to = 5),
        // v6: the customer's map pin on bookings and jobs (nullable columns).
        AutoMigration(from = 5, to = 6),
        // v7: payment method and status on bookings and jobs (nullable columns).
        AutoMigration(from = 6, to = 7),
    ],
)
@TypeConverters(Converters::class)
abstract class SahyogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun bookingDao(): BookingDao

    abstract fun jobDao(): JobDao

    abstract fun reviewDao(): ReviewDao
}
