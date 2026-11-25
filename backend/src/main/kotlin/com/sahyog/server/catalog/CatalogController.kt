package com.sahyog.server.catalog

import com.sahyog.server.api.ValidationException
import com.sahyog.server.worker.LatLng
import com.sahyog.server.worker.ServiceDto
import com.sahyog.server.worker.WorkerRecord
import com.sahyog.server.worker.WorkerRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CategoryDto(val id: String, val name: String, val keywords: List<String>)

/** A verified worker as customers see them (the app's `NetworkWorker`). */
data class PublicWorker(
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
    val services: List<ServiceDto>,
    val isAvailable: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    /** Only in "near me" results. */
    val distanceKm: Double? = null,
)

data class CatalogResponse(val categories: List<CategoryDto>, val workers: List<PublicWorker>)

@RestController
@RequestMapping("/api/v1")
class CatalogController(private val workers: WorkerRepository, private val jdbc: JdbcClient) {

    /** Everything the customer app browses: categories and every verified worker. */
    @GetMapping("/catalog")
    fun catalog(): CatalogResponse = CatalogResponse(categories(), workers.verified().map { it.toPublic() })

    /**
     * Verified workers within [radiusKm] of a point, nearest first. The point is rounded to
     * ~1 km before use: the server never needs (or keeps) a customer's exact position.
     */
    @GetMapping("/workers/nearby")
    fun nearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "10") radiusKm: Double,
        @RequestParam(required = false) categoryId: String?,
    ): List<PublicWorker> {
        val point = LatLng(lat, lng)
        if (!point.isValid) throw ValidationException("location")
        if (radiusKm !in 0.5..MAX_RADIUS_KM) throw ValidationException("radius")
        return workers.verifiedNear(point.coarsened(), radiusKm, categoryId).map { it.toPublic() }
    }

    private fun categories(): List<CategoryDto> =
        jdbc.sql("SELECT * FROM categories ORDER BY position").query { rs, _ ->
            CategoryDto(
                id = rs.getString("id"),
                name = rs.getString("name"),
                keywords = (rs.getArray("keywords").array as Array<*>).map { it.toString() },
            )
        }.list()

    // Ratings and completed jobs are computed from reviews and bookings (M10c); new until then.
    private fun WorkerRecord.toPublic() = PublicWorker(
        id = uid,
        name = name,
        categoryIds = categoryIds,
        rating = 0f,
        ratingCount = 0,
        jobsCompleted = 0,
        yearsExperience = yearsExperience,
        locality = locality,
        about = about,
        languages = languages,
        services = services,
        isAvailable = isAvailable,
        latitude = location?.latitude,
        longitude = location?.longitude,
        distanceKm = distanceKm,
    )

    private companion object {
        const val MAX_RADIUS_KM = 50.0
    }
}
