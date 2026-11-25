package com.sahyog.core.data.review

import com.sahyog.core.data.network.SahyogApi
import javax.inject.Inject

internal class RetrofitReviewDataSource @Inject constructor(
    private val api: SahyogApi,
) : ReviewNetworkDataSource {
    override suspend fun fetchWorkerReviews(workerId: String): List<NetworkWorkerReview> = api.reviews(workerId)
}
