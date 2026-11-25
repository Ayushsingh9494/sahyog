package com.sahyog.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sahyog.core.model.WorkerReview
import java.time.Instant

/**
 * Cached public review shown on a worker's profile. Public data (first names only),
 * like the catalog, so it isn't cleared on sign-out.
 */
@Entity(tableName = "worker_reviews", indices = [Index("worker_id")])
data class ReviewEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    val rating: Int,
    val comment: String?,
    @ColumnInfo(name = "service_name") val serviceName: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

fun ReviewEntity.asExternalModel() = WorkerReview(
    id = id,
    workerId = workerId,
    authorName = authorName,
    rating = rating,
    comment = comment,
    serviceName = serviceName,
    createdAt = createdAt,
)

fun WorkerReview.asEntity() = ReviewEntity(
    id = id,
    workerId = workerId,
    authorName = authorName,
    rating = rating,
    comment = comment,
    serviceName = serviceName,
    createdAt = createdAt,
)
