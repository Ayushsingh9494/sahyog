package com.sahyog.core.testing

import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.repository.ReviewRepository
import com.sahyog.core.model.WorkerReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory reviews; [refresh] only counts calls and returns [refreshResult]. */
class TestReviewRepository : ReviewRepository {
    private val all = MutableStateFlow<List<WorkerReview>>(emptyList())

    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    val refreshedWorkers = mutableListOf<String>()

    fun setReviews(reviews: List<WorkerReview>) = all.update { reviews }

    override fun reviews(workerId: String): Flow<List<WorkerReview>> =
        all.map { list -> list.filter { it.workerId == workerId }.sortedByDescending { it.createdAt } }

    override suspend fun refresh(workerId: String): AppResult<Unit> {
        refreshedWorkers += workerId
        return refreshResult
    }
}

object TestReviews {
    fun review(id: String, workerId: String, rating: Int = 5, createdAtMillis: Long = 0) = WorkerReview(
        id = id,
        workerId = workerId,
        authorName = "Asha",
        rating = rating,
        comment = "On time and tidy.",
        serviceName = "Fan installation",
        createdAt = Instant.ofEpochMilli(createdAtMillis),
    )
}
