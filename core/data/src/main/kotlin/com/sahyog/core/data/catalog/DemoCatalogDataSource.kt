package com.sahyog.core.data.catalog

import com.sahyog.core.model.CategoryIds
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * TEMPORARY catalog source with realistic sample data, standing in for the backend
 * until Milestone 10. It simulates network latency so loading states are exercised.
 * All people and numbers here are fictional.
 */
internal class DemoCatalogDataSource @Inject constructor() : CatalogNetworkDataSource {

    override suspend fun fetchCatalog(): NetworkCatalog {
        delay(SIMULATED_LATENCY_MS)
        return NetworkCatalog(categories = categories, workers = workers)
    }

    private companion object {
        const val SIMULATED_LATENCY_MS = 700L

        val categories = listOf(
            NetworkCategory(
                CategoryIds.ELECTRICIAN, "Electrician",
                listOf("electric", "wiring", "fan", "light", "switch", "बिजली", "इलेक्ट्रीशियन", "मिस्त्री"),
            ),
            NetworkCategory(
                CategoryIds.PLUMBER, "Plumber",
                listOf("pipe", "tap", "leak", "water", "नल", "प्लंबर", "पानी"),
            ),
            NetworkCategory(
                CategoryIds.CARPENTER, "Carpenter",
                listOf("wood", "furniture", "door", "बढ़ई", "कारपेंटर", "फर्नीचर"),
            ),
            NetworkCategory(
                CategoryIds.PAINTER, "Painter",
                listOf("paint", "wall", "colour", "पेंटर", "रंगाई", "पुताई"),
            ),
            NetworkCategory(
                CategoryIds.CLEANING, "Cleaning",
                listOf("cleaner", "deep clean", "maid", "सफाई", "सफ़ाई"),
            ),
            NetworkCategory(
                CategoryIds.APPLIANCE_REPAIR, "Appliance repair",
                listOf("washing machine", "fridge", "microwave", "RO", "मशीन", "फ्रिज", "रिपेयर"),
            ),
            NetworkCategory(
                CategoryIds.AC_REPAIR, "AC repair",
                listOf("air conditioner", "cooling", "एसी"),
            ),
            NetworkCategory(
                CategoryIds.PEST_CONTROL, "Pest control",
                listOf("cockroach", "termite", "insects", "कीट", "दीमक", "कॉकरोच"),
            ),
        )

        private fun service(name: String, rupees: Long, unit: String = "PerVisit") =
            NetworkWorkerService(name, rupees * 100, unit)

        val workers = listOf(
            NetworkWorker(
                id = "w-ramesh", name = "Ramesh Kumar",
                categoryIds = listOf(CategoryIds.ELECTRICIAN, CategoryIds.APPLIANCE_REPAIR),
                rating = 4.8f, ratingCount = 212, jobsCompleted = 486, yearsExperience = 12,
                locality = "Model Town",
                about = "Licensed electrician handling house wiring, fan and light fittings, and inverter setup. " +
                    "Carries his own tools and spare switches.",
                languages = listOf("Hindi", "Punjabi"),
                services = listOf(
                    service("Fan installation", 199),
                    service("Switch & socket repair", 149),
                    service("House wiring", 299, "PerHour"),
                    service("Inverter installation", 499, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-sunil", name = "Sunil Verma",
                categoryIds = listOf(CategoryIds.ELECTRICIAN),
                rating = 4.6f, ratingCount = 98, jobsCompleted = 173, yearsExperience = 6,
                locality = "Civil Lines",
                about = "Quick fixes for tripping MCBs, short circuits and new light points.",
                languages = listOf("Hindi", "English"),
                services = listOf(
                    service("MCB / fuse repair", 199),
                    service("Light point installation", 129),
                    service("Doorbell installation", 149),
                ),
                isAvailable = false,
            ),
            NetworkWorker(
                id = "w-imran", name = "Mohd. Imran",
                categoryIds = listOf(CategoryIds.ELECTRICIAN, CategoryIds.AC_REPAIR),
                rating = 4.7f, ratingCount = 156, jobsCompleted = 301, yearsExperience = 9,
                locality = "Gandhi Nagar",
                about = "Electrician and AC technician. Specialises in split AC installation and stabiliser wiring.",
                languages = listOf("Hindi", "Urdu"),
                services = listOf(
                    service("AC installation", 1499, "PerJob"),
                    service("Stabiliser installation", 249),
                    service("Switchboard repair", 149),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-rajesh", name = "Rajesh Yadav",
                categoryIds = listOf(CategoryIds.PLUMBER),
                rating = 4.7f, ratingCount = 184, jobsCompleted = 392, yearsExperience = 11,
                locality = "Rajendra Nagar",
                about = "Leaks, blockages and bathroom fittings. Same-day visits in most areas.",
                languages = listOf("Hindi"),
                services = listOf(
                    service("Tap repair", 149),
                    service("Leak repair", 249),
                    service("Toilet repair", 349),
                    service("Water tank cleaning", 599, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-gurpreet", name = "Gurpreet Singh",
                categoryIds = listOf(CategoryIds.PLUMBER),
                rating = 4.5f, ratingCount = 77, jobsCompleted = 140, yearsExperience = 5,
                locality = "Shastri Nagar",
                about = "New pipe fittings, geyser connections and motor installation.",
                languages = listOf("Punjabi", "Hindi"),
                services = listOf(
                    service("Pipe fitting", 299, "PerHour"),
                    service("Geyser installation", 449, "PerJob"),
                    service("Water motor installation", 499, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-vinod", name = "Vinod Sharma",
                categoryIds = listOf(CategoryIds.PLUMBER),
                rating = 4.3f, ratingCount = 41, jobsCompleted = 66, yearsExperience = 3,
                locality = "Old City",
                about = "Affordable plumbing repairs for homes and small shops.",
                languages = listOf("Hindi"),
                services = listOf(
                    service("Drain unblocking", 199),
                    service("Wash basin installation", 399, "PerJob"),
                ),
                isAvailable = false,
            ),
            NetworkWorker(
                id = "w-harish", name = "Harish Vishwakarma",
                categoryIds = listOf(CategoryIds.CARPENTER),
                rating = 4.9f, ratingCount = 263, jobsCompleted = 540, yearsExperience = 18,
                locality = "Sector 14",
                about = "Third-generation carpenter. Furniture repair, polishing and custom shelves.",
                languages = listOf("Hindi"),
                services = listOf(
                    service("Furniture repair", 249),
                    service("Door lock installation", 199),
                    service("Bed assembly", 499, "PerJob"),
                    service("Wood polishing", 349, "PerHour"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-baldev", name = "Baldev Raj",
                categoryIds = listOf(CategoryIds.CARPENTER),
                rating = 4.4f, ratingCount = 58, jobsCompleted = 112, yearsExperience = 7,
                locality = "Railway Colony",
                about = "Modular kitchen and wardrobe repairs, hinges and channels.",
                languages = listOf("Hindi", "Punjabi"),
                services = listOf(
                    service("Kitchen cabinet repair", 399, "PerHour"),
                    service("Hinge replacement", 149),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-anil", name = "Anil Prajapati",
                categoryIds = listOf(CategoryIds.PAINTER),
                rating = 4.6f, ratingCount = 91, jobsCompleted = 128, yearsExperience = 10,
                locality = "Civil Lines",
                about = "Interior and exterior painting with a small team. Free colour consultation.",
                languages = listOf("Hindi"),
                services = listOf(
                    service("Room painting", 2999, "PerJob"),
                    service("Wall touch-up", 799),
                    service("Waterproofing", 1499, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-salim", name = "Salim Ansari",
                categoryIds = listOf(CategoryIds.PAINTER),
                rating = 4.5f, ratingCount = 64, jobsCompleted = 97, yearsExperience = 8,
                locality = "Gandhi Nagar",
                about = "Texture paints, wood polish and grill painting.",
                languages = listOf("Hindi", "Urdu"),
                services = listOf(
                    service("Texture wall", 1999, "PerJob"),
                    service("Grill & gate painting", 699, "PerJob"),
                ),
                isAvailable = false,
            ),
            NetworkWorker(
                id = "w-sunita", name = "Sunita Devi",
                categoryIds = listOf(CategoryIds.CLEANING),
                rating = 4.8f, ratingCount = 241, jobsCompleted = 610, yearsExperience = 7,
                locality = "Model Town",
                about = "Leads a three-person cleaning team. Uses safe, cooperative-supplied cleaning products.",
                languages = listOf("Hindi", "Punjabi"),
                services = listOf(
                    service("Home deep cleaning", 1999, "PerJob"),
                    service("Kitchen cleaning", 799, "PerJob"),
                    service("Bathroom cleaning", 449, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-meena", name = "Meena Kumari",
                categoryIds = listOf(CategoryIds.CLEANING),
                rating = 4.7f, ratingCount = 133, jobsCompleted = 288, yearsExperience = 5,
                locality = "Rajendra Nagar",
                about = "Sofa, carpet and mattress cleaning with a wet-vacuum machine.",
                languages = listOf("Hindi"),
                services = listOf(
                    service("Sofa cleaning", 599, "PerJob"),
                    service("Carpet cleaning", 499, "PerJob"),
                    service("Mattress cleaning", 449, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-kavita", name = "Kavita Rani",
                categoryIds = listOf(CategoryIds.CLEANING),
                rating = 4.4f, ratingCount = 52, jobsCompleted = 90, yearsExperience = 2,
                locality = "Shastri Nagar",
                about = "Regular and move-in cleaning for flats.",
                languages = listOf("Hindi", "Punjabi"),
                services = listOf(
                    service("Move-in cleaning", 1499, "PerJob"),
                    service("Window cleaning", 299, "PerHour"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-deepak", name = "Deepak Gupta",
                categoryIds = listOf(CategoryIds.APPLIANCE_REPAIR, CategoryIds.AC_REPAIR),
                rating = 4.6f, ratingCount = 147, jobsCompleted = 330, yearsExperience = 9,
                locality = "Sector 14",
                about = "Repairs washing machines, fridges and ACs of all major brands. Genuine spare parts with bill.",
                languages = listOf("Hindi", "English"),
                services = listOf(
                    service("Washing machine repair", 349),
                    service("Fridge repair", 399),
                    service("AC service", 499),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-arjun", name = "Arjun Mehta",
                categoryIds = listOf(CategoryIds.APPLIANCE_REPAIR),
                rating = 4.3f, ratingCount = 39, jobsCompleted = 71, yearsExperience = 4,
                locality = "Old City",
                about = "Microwave, mixer and RO purifier servicing.",
                languages = listOf("Hindi", "Punjabi"),
                services = listOf(
                    service("Microwave repair", 299),
                    service("RO purifier service", 349),
                    service("Mixer grinder repair", 199),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-farhan", name = "Farhan Qureshi",
                categoryIds = listOf(CategoryIds.AC_REPAIR),
                rating = 4.7f, ratingCount = 118, jobsCompleted = 254, yearsExperience = 8,
                locality = "Railway Colony",
                about = "Split and window AC servicing, gas refill and leak testing.",
                languages = listOf("Hindi", "Urdu"),
                services = listOf(
                    service("AC service", 499),
                    service("Gas refill", 2499, "PerJob"),
                    service("AC uninstallation", 599, "PerJob"),
                ),
                isAvailable = false,
            ),
            NetworkWorker(
                id = "w-manoj", name = "Manoj Tiwari",
                categoryIds = listOf(CategoryIds.PEST_CONTROL),
                rating = 4.5f, ratingCount = 86, jobsCompleted = 205, yearsExperience = 10,
                locality = "Civil Lines",
                about = "Certified pest control using government-approved, odourless chemicals.",
                languages = listOf("Hindi"),
                services = listOf(
                    service("Cockroach control", 899, "PerJob"),
                    service("Termite treatment", 2999, "PerJob"),
                    service("Bed bug treatment", 1499, "PerJob"),
                ),
                isAvailable = true,
            ),
            NetworkWorker(
                id = "w-pooja", name = "Pooja Saini",
                categoryIds = listOf(CategoryIds.PEST_CONTROL, CategoryIds.CLEANING),
                rating = 4.6f, ratingCount = 61, jobsCompleted = 119, yearsExperience = 4,
                locality = "Gandhi Nagar",
                about = "Pest control with a post-treatment clean-up, ideal for kitchens.",
                languages = listOf("Hindi", "Punjabi", "English"),
                services = listOf(
                    service("Kitchen pest control", 999, "PerJob"),
                    service("Mosquito treatment", 699, "PerJob"),
                ),
                isAvailable = true,
            ),
        )
    }
}
