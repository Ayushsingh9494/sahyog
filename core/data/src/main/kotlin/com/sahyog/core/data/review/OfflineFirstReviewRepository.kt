package com.sahyog.core.data.review

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.network.apiCall
import com.sahyog.core.data.repository.ReviewRepository
import com.sahyog.core.database.dao.ReviewDao
import com.sahyog.core.database.entity.asEntity
import com.sahyog.core.database.entity.asExternalModel
import com.sahyog.core.model.WorkerReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Reviews are read from the Room cache; [refresh] replaces one worker's cached reviews. */
@Singleton
internal class OfflineFirstReviewRepository @Inject constructor(
    private val dao: ReviewDao,
    private val network: ReviewNetworkDataSource,
) : ReviewRepository {

    override fun reviews(workerId: String): Flow<List<WorkerReview>> = dao.observeReviews(workerId)
        .map { entities -> entities.map { it.asExternalModel() } }
        .distinctUntilChanged()

    override suspend fun refresh(workerId: String): AppResult<Unit> = apiCall {
        val reviews = network.fetchWorkerReviews(workerId).map { it.asExternalModel() }
        dao.replaceForWorker(workerId, reviews.map { it.asEntity() })
    }
}

internal fun NetworkWorkerReview.asExternalModel() = WorkerReview(
    id = id,
    workerId = workerId,
    authorName = authorName,
    rating = rating,
    comment = comment,
    serviceName = serviceName,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)
