package com.sahyog.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * @property keywords Extra search terms (synonyms, Hindi words) so "बिजली" finds electricians.
 * @property position Display order chosen by the cooperative.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val keywords: String,
    val position: Int,
)

/**
 * @property startingPricePaise Denormalised minimum service price, so lists can sort/filter without joins.
 */
@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rating: Float,
    @ColumnInfo(name = "rating_count") val ratingCount: Int,
    @ColumnInfo(name = "jobs_completed") val jobsCompleted: Int,
    @ColumnInfo(name = "years_experience") val yearsExperience: Int,
    val locality: String,
    val about: String,
    val languages: List<String>,
    @ColumnInfo(name = "is_available") val isAvailable: Boolean,
    @ColumnInfo(name = "starting_price_paise") val startingPricePaise: Long?,
)

/** Which categories a worker serves (many-to-many). */
@Entity(
    tableName = "worker_categories",
    primaryKeys = ["worker_id", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkerEntity::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class WorkerCategoryCrossRef(
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
)

/** @property unit A [com.sahyog.core.model.PriceUnit] name. */
@Entity(
    tableName = "worker_services",
    primaryKeys = ["worker_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = WorkerEntity::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkerServiceEntity(
    @ColumnInfo(name = "worker_id") val workerId: String,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "price_paise") val pricePaise: Long,
    val unit: String,
)

/** A worker with everything a screen needs, loaded by Room in one transaction. */
data class PopulatedWorker(
    @Embedded val worker: WorkerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = WorkerCategoryCrossRef::class,
            parentColumn = "worker_id",
            entityColumn = "category_id",
        ),
    )
    val categories: List<CategoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "worker_id")
    val services: List<WorkerServiceEntity>,
)
