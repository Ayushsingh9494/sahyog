package com.sahyog.core.model

/** Identity documents the cooperative accepts for verification. */
enum class IdDocumentType {
    Aadhaar,
    VoterId,
    DrivingLicence,
    Pan,
    ;

    companion object {
        /** Upload limit for a document photo. */
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    }
}

/**
 * The signed-in worker's own profile, as they manage it (customers see the public
 * [Worker] built from it once the cooperative has verified them).
 */
data class WorkerProfile(
    val categoryIds: List<String>,
    val services: List<WorkerService>,
    val about: String,
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
    val isAvailable: Boolean,
    val verificationStatus: WorkerVerificationStatus,
    /** Which document was submitted; null until one is. The image itself is never returned. */
    val idDocumentType: IdDocumentType?,
    /** Where they work from, coarsened to ~1 km; null if not set. */
    val location: GeoPoint? = null,
)

/** What the worker fills in on the profile form. */
data class WorkerProfileInput(
    val categoryIds: List<String>,
    val services: List<WorkerService>,
    val about: String,
    val locality: String,
    val languages: List<String>,
    val yearsExperience: Int,
    val location: GeoPoint? = null,
) {
    val isValid: Boolean
        get() = isValid(
            categoryCount = categoryIds.distinct().size,
            services = services.map { it.name to it.startingPrice.value },
            about = about,
            locality = locality,
            languageCount = languages.distinct().size,
            yearsExperience = yearsExperience,
        )

    companion object {
        val CATEGORY_COUNT = 1..4
        val SERVICE_COUNT = 1..8
        val SERVICE_NAME_LENGTH = 3..40
        val SERVICE_PRICE_RUPEES = 50L..50_000L
        const val ABOUT_MAX_LENGTH = 300
        val LOCALITY_LENGTH = 3..60
        val LANGUAGE_COUNT = 1..6
        val YEARS_EXPERIENCE = 0..60

        /** Languages offered on the profile form. */
        val LANGUAGES = listOf("Hindi", "English", "Punjabi", "Urdu", "Bengali", "Marathi", "Gujarati", "Tamil", "Telugu")

        /** Shared by the form (client) and the server, so both enforce the same rules. */
        fun isValid(
            categoryCount: Int,
            services: List<Pair<String, Long>>,
            about: String,
            locality: String,
            languageCount: Int,
            yearsExperience: Int,
        ): Boolean =
            categoryCount in CATEGORY_COUNT &&
                services.size in SERVICE_COUNT &&
                services.all { (name, paise) ->
                    name.trim().length in SERVICE_NAME_LENGTH && paise % 100 == 0L && paise / 100 in SERVICE_PRICE_RUPEES
                } &&
                services.map { it.first.trim().lowercase() }.distinct().size == services.size &&
                about.trim().length <= ABOUT_MAX_LENGTH &&
                locality.trim().length in LOCALITY_LENGTH &&
                languageCount in LANGUAGE_COUNT &&
                yearsExperience in YEARS_EXPERIENCE
    }
}
