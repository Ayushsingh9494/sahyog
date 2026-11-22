package com.sahyog.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sahyog.core.database.dao.CatalogDao
import com.sahyog.core.database.entity.CategoryEntity
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SahyogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
