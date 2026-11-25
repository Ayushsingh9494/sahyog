package com.sahyog.server.worker

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import kotlin.math.round

/** Field names in validation errors, as in the app's `WorkerErrorFields`. */
object WorkerErrorFields {
    const val PROFILE = "profile"
    const val DOCUMENT = "document"
    const val DOCUMENT_TOO_LARGE = "document_too_large"
    const val NO_PROFILE = "no_profile"
}

enum class PriceUnit { PerVisit, PerHour, PerJob }

enum class IdDocumentType { Aadhaar, VoterId, DrivingLicence, Pan }

data class ServiceDto(val name: String, val pricePaise: Long, val unit: PriceUnit)

/** A point in degrees. */
data class LatLng(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude in -90.0..90.0 && longitude in -180.0..180.0

    /** ~1 km: the most we ever keep of where someone lives or works from. */
    fun coarsened() = LatLng(latitude.round2(), longitude.round2())

    private fun Double.round2() = round(this * 100) / 100
}

/**
 * The rules of the app's worker profile form (`WorkerProfileInput`), enforced again here
 * because the server can't trust any client.
 */
object WorkerProfileRules {
    val CATEGORY_COUNT = 1..4
    val SERVICE_COUNT = 1..8
    val SERVICE_NAME_LENGTH = 3..40
    val SERVICE_PRICE_RUPEES = 50L..50_000L
    const val ABOUT_MAX_LENGTH = 300
    val LOCALITY_LENGTH = 3..60
    val LANGUAGE_COUNT = 1..6
    val YEARS_EXPERIENCE = 0..60
    val LANGUAGES = setOf("Hindi", "English", "Punjabi", "Urdu", "Bengali", "Marathi", "Gujarati", "Tamil", "Telugu")

    /** ID photos, as in the app's `IdDocumentType.MAX_IMAGE_BYTES`. */
    const val MAX_DOCUMENT_BYTES = 10 * 1024 * 1024

    fun isValid(input: WorkerProfileInput, knownCategories: Set<String>): Boolean {
        val categories = input.categoryIds.distinct()
        val languages = input.languages.distinct()
        val names = input.services.map { it.name.trim() }
        return categories.size in CATEGORY_COUNT && knownCategories.containsAll(categories) &&
            input.services.size in SERVICE_COUNT &&
            input.services.all { s ->
                s.name.trim().length in SERVICE_NAME_LENGTH &&
                    s.pricePaise % 100 == 0L && s.pricePaise / 100 in SERVICE_PRICE_RUPEES
            } &&
            names.map { it.lowercase() }.distinct().size == names.size &&
            input.about.trim().length <= ABOUT_MAX_LENGTH &&
            input.locality.trim().length in LOCALITY_LENGTH &&
            languages.size in LANGUAGE_COUNT && LANGUAGES.containsAll(languages) &&
            input.yearsExperience in YEARS_EXPERIENCE &&
            // Both coordinates or neither, and on Earth.
            (input.latitude == null) == (input.longitude == null) &&
            input.location()?.isValid != false
    }
}

/** What the worker submits from the profile form (the app's `NetworkWorkerProfileInput`). */
data class WorkerProfileInput(
    val categoryIds: List<String>,
    val services: List<ServiceDto>,
    val about: String = "",
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    fun location(): LatLng? = if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
}

/** A worker profile as stored, with the user's name and verification status. */
data class WorkerRecord(
    val uid: String,
    val name: String,
    val status: String,
    val categoryIds: List<String>,
    val services: List<ServiceDto>,
    val about: String,
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
    val isAvailable: Boolean,
    val location: LatLng?,
    /** Straight-line km from the point a query was made for; null otherwise. */
    val distanceKm: Double? = null,
    /** Average of customers' ratings (0 when nobody has rated yet) and how many there are. */
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val jobsCompleted: Int = 0,
)

data class DocumentRecord(
    val id: Long,
    val uid: String,
    val type: IdDocumentType,
    val storageKey: String,
    val contentType: String,
    val submittedAt: Instant,
    val decision: String?,
)

@Repository
class WorkerRepository(private val jdbc: JdbcClient) {

    fun categoryIds(): Set<String> =
        jdbc.sql("SELECT id FROM categories").query { rs, _ -> rs.getString("id") }.set()

    fun find(uid: String): WorkerRecord? = query("WHERE p.uid = :uid", mapOf("uid" to uid)).firstOrNull()

    /** Workers customers may see: verified by the cooperative. */
    fun verified(): List<WorkerRecord> = query("WHERE u.worker_status = 'Verified' AND u.is_worker", emptyMap())

    /**
     * Verified workers within [radiusKm] of [point], nearest first, optionally in one category.
     * Uses the PostGIS index on `worker_profiles.location`.
     */
    fun verifiedNear(point: LatLng, radiusKm: Double, categoryId: String?): List<WorkerRecord> = query(
        """
        WHERE u.worker_status = 'Verified' AND u.is_worker
          AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
          AND (CAST(:category AS text) IS NULL OR EXISTS (
              SELECT 1 FROM worker_categories c WHERE c.uid = p.uid AND c.category_id = :category))
        """.trimIndent(),
        mapOf("lat" to point.latitude, "lng" to point.longitude, "radius" to radiusKm * 1000, "category" to categoryId),
        distanceFrom = point,
    )

    @Transactional
    fun save(uid: String, input: WorkerProfileInput, location: LatLng?) {
        jdbc.sql(
            """
            INSERT INTO worker_profiles (uid, about, locality, languages, years_experience, location)
            VALUES (:uid, :about, :locality, string_to_array(:languages, ','), :years,
                    ST_SetSRID(ST_MakePoint(CAST(:lng AS double precision), CAST(:lat AS double precision)), 4326)::geography)
            ON CONFLICT (uid) DO UPDATE SET
                about = EXCLUDED.about, locality = EXCLUDED.locality, languages = EXCLUDED.languages,
                years_experience = EXCLUDED.years_experience, location = EXCLUDED.location, updated_at = now()
            """.trimIndent(),
        )
            .param("uid", uid)
            .param("about", input.about.trim())
            .param("locality", input.locality.trim())
            // Languages come from a fixed list without commas (checked by WorkerProfileRules).
            .param("languages", input.languages.distinct().joinToString(","))
            .param("years", input.yearsExperience)
            .param("lat", location?.latitude)
            .param("lng", location?.longitude)
            .update()

        jdbc.sql("DELETE FROM worker_categories WHERE uid = :uid").param("uid", uid).update()
        input.categoryIds.distinct().forEach { category ->
            jdbc.sql("INSERT INTO worker_categories (uid, category_id) VALUES (:uid, :category)")
                .param("uid", uid).param("category", category).update()
        }
        jdbc.sql("DELETE FROM worker_services WHERE uid = :uid").param("uid", uid).update()
        input.services.forEachIndexed { position, service ->
            jdbc.sql(
                "INSERT INTO worker_services (uid, position, name, price_paise, unit) VALUES (:uid, :pos, :name, :price, :unit)",
            )
                .param("uid", uid).param("pos", position).param("name", service.name.trim())
                .param("price", service.pricePaise).param("unit", service.unit.name)
                .update()
        }
    }

    fun setAvailability(uid: String, available: Boolean) {
        jdbc.sql("UPDATE worker_profiles SET is_available = :available, updated_at = now() WHERE uid = :uid")
            .param("uid", uid).param("available", available).update()
    }

    fun addDocument(uid: String, type: IdDocumentType, storageKey: String, contentType: String): Long =
        jdbc.sql(
            """
            INSERT INTO id_documents (uid, document_type, storage_key, content_type)
            VALUES (:uid, :type, :key, :contentType) RETURNING id
            """.trimIndent(),
        )
            .param("uid", uid).param("type", type.name).param("key", storageKey).param("contentType", contentType)
            .query(Long::class.java)
            .single()

    fun latestDocument(uid: String): DocumentRecord? = jdbc.sql(
        "SELECT * FROM id_documents WHERE uid = :uid ORDER BY submitted_at DESC, id DESC LIMIT 1",
    ).param("uid", uid).query { rs, _ -> rs.toDocument() }.optional().orElse(null)

    fun decide(documentId: Long, decision: String, adminUid: String, reason: String?) {
        jdbc.sql(
            """
            UPDATE id_documents SET decision = :decision, decided_by = :admin, decided_at = now(),
                rejection_reason = :reason
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", documentId).param("decision", decision).param("admin", adminUid).param("reason", reason)
            .update()
    }

    private fun query(where: String, params: Map<String, Any?>, distanceFrom: LatLng? = null): List<WorkerRecord> {
        val distance = if (distanceFrom != null) {
            "ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000"
        } else {
            "NULL"
        }
        val rows = jdbc.sql(
            """
            SELECT p.*, u.name, u.worker_status,
                   ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lng,
                   $distance AS distance_km,
                   COALESCE(s.rating, 0) AS rating, COALESCE(s.rating_count, 0) AS rating_count,
                   COALESCE(s.jobs_completed, 0) AS jobs_completed
            FROM worker_profiles p JOIN users u ON u.uid = p.uid
            LEFT JOIN LATERAL (
                SELECT avg(b.review_rating) AS rating, count(b.review_rating) AS rating_count,
                       count(*) FILTER (WHERE b.status = 'Completed') AS jobs_completed
                FROM bookings b WHERE b.worker_uid = p.uid
            ) s ON true
            $where
            ORDER BY ${if (distanceFrom != null) "distance_km" else "u.name"}
            """.trimIndent(),
        ).params(params).query { rs, _ -> rs.toRow() }.list()
        if (rows.isEmpty()) return emptyList()

        // Categories and services for all rows in two queries (not one per worker).
        val uids = rows.map { it.uid }
        val categories = jdbc.sql(
            """
            SELECT wc.uid, wc.category_id FROM worker_categories wc JOIN categories c ON c.id = wc.category_id
            WHERE wc.uid IN (:uids) ORDER BY c.position
            """.trimIndent(),
        ).param("uids", uids).query { rs, _ -> rs.getString("uid") to rs.getString("category_id") }.list()
            .groupBy({ it.first }, { it.second })
        val services = jdbc.sql("SELECT * FROM worker_services WHERE uid IN (:uids) ORDER BY position")
            .param("uids", uids)
            .query { rs, _ ->
                rs.getString("uid") to ServiceDto(
                    rs.getString("name"),
                    rs.getLong("price_paise"),
                    PriceUnit.valueOf(rs.getString("unit")),
                )
            }.list().groupBy({ it.first }, { it.second })
        return rows.map { it.copy(categoryIds = categories[it.uid].orEmpty(), services = services[it.uid].orEmpty()) }
    }

    private fun ResultSet.toRow(): WorkerRecord {
        val lat = getDouble("lat").takeUnless { wasNull() }
        val lng = getDouble("lng").takeUnless { wasNull() }
        return WorkerRecord(
            uid = getString("uid"),
            name = getString("name"),
            status = getString("worker_status") ?: "NotSubmitted",
            categoryIds = emptyList(),
            services = emptyList(),
            about = getString("about"),
            locality = getString("locality"),
            languages = (getArray("languages").array as Array<*>).map { it.toString() },
            yearsExperience = getInt("years_experience"),
            isAvailable = getBoolean("is_available"),
            location = if (lat != null && lng != null) LatLng(lat, lng) else null,
            distanceKm = getDouble("distance_km").takeUnless { wasNull() },
            rating = getDouble("rating"),
            ratingCount = getInt("rating_count"),
            jobsCompleted = getInt("jobs_completed"),
        )
    }

    private fun ResultSet.toDocument() = DocumentRecord(
        id = getLong("id"),
        uid = getString("uid"),
        type = IdDocumentType.valueOf(getString("document_type")),
        storageKey = getString("storage_key"),
        contentType = getString("content_type"),
        submittedAt = getTimestamp("submitted_at").toInstant(),
        decision = getString("decision"),
    )
}
