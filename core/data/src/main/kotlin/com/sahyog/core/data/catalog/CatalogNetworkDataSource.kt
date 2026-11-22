package com.sahyog.core.data.catalog

import kotlinx.serialization.Serializable

/**
 * Where the service catalog comes from. Implemented by [DemoCatalogDataSource] until
 * the Spring Boot API exists (Milestone 10), when a Retrofit implementation replaces it.
 * Its shapes mirror the planned API response, so the swap doesn't touch the repository.
 *
 * Implementations throw on failure (IOException for connectivity problems); the
 * repository maps exceptions to AppError.
 */
interface CatalogNetworkDataSource {
    suspend fun fetchCatalog(): NetworkCatalog
}

data class NetworkCatalog(
    val categories: List<NetworkCategory>,
    val workers: List<NetworkWorker>,
)

/** @property keywords Search synonyms, including Hindi terms. */
data class NetworkCategory(
    val id: String,
    val name: String,
    val keywords: List<String>,
)

data class NetworkWorker(
    val id: String,
    val name: String,
    val categoryIds: List<String>,
    val rating: Float,
    val ratingCount: Int,
    val jobsCompleted: Int,
    val yearsExperience: Int,
    val locality: String,
    val about: String,
    val languages: List<String>,
    val services: List<NetworkWorkerService>,
    val isAvailable: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** @property unit "PerVisit", "PerHour" or "PerJob". */
@Serializable
data class NetworkWorkerService(
    val name: String,
    val pricePaise: Long,
    val unit: String,
)
