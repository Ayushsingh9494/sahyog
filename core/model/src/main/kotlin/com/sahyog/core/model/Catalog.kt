package com.sahyog.core.model

/**
 * A kind of service the cooperative offers (electrician, plumber, ...).
 *
 * The taxonomy is owned by the cooperative (backend). [id] is stable and used by the
 * client to pick an icon and a translated label for known categories; [name] is the
 * server's display name, used as a fallback for categories the app doesn't know yet.
 */
data class ServiceCategory(
    val id: String,
    val name: String,
)

/** Well-known category ids. The backend may add more; unknown ids still display via [ServiceCategory.name]. */
object CategoryIds {
    const val ELECTRICIAN = "electrician"
    const val PLUMBER = "plumber"
    const val CARPENTER = "carpenter"
    const val PAINTER = "painter"
    const val CLEANING = "cleaning"
    const val APPLIANCE_REPAIR = "appliance_repair"
    const val AC_REPAIR = "ac_repair"
    const val PEST_CONTROL = "pest_control"
}

/** Money is always integer paise, never floating point. */
@JvmInline
value class Paise(val value: Long) : Comparable<Paise> {
    override fun compareTo(other: Paise): Int = value.compareTo(other.value)

    companion object {
        fun rupees(rupees: Long) = Paise(rupees * 100)
    }
}

/** How a service is priced. */
enum class PriceUnit { PerVisit, PerHour, PerJob }

/** One thing a worker offers, with an indicative starting price. */
data class WorkerService(
    val name: String,
    val startingPrice: Paise,
    val unit: PriceUnit,
)

/**
 * A cooperative member as shown to customers. Only cooperative-verified workers
 * are ever returned to customers, so there is no verification flag here.
 *
 * @property locality Neighbourhood they work from, shown to customers.
 * @property location Approximate (~1 km) point they work from; null if they never set one.
 */
data class Worker(
    val id: String,
    val name: String,
    val categories: List<ServiceCategory>,
    val rating: Float,
    val ratingCount: Int,
    val jobsCompleted: Int,
    val yearsExperience: Int,
    val locality: String,
    val about: String,
    val languages: List<String>,
    val services: List<WorkerService>,
    val isAvailable: Boolean,
    val location: GeoPoint? = null,
) {
    /** Lowest price across services: the "from ₹X" shown on cards. */
    val startingPrice: Paise?
        get() = services.minOfOrNull { it.startingPrice }
}
