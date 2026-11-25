package com.sahyog.server.worker

import com.sahyog.server.ApiTest
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class WorkerVerificationFlowTest : ApiTest() {

    private fun profileJson(
        categories: String = """["electrician"]""",
        price: Long = 30_000,
        languages: String = """["Hindi","Punjabi"]""",
        location: String = """"latitude":31.308512,"longitude":75.587134""",
    ) = """
        {"categoryIds":$categories,
         "services":[{"name":" Fan repair ","pricePaise":$price,"unit":"PerVisit"}],
         "about":"Ten years of house wiring.","locality":"Model Town","languages":$languages,
         "yearsExperience":10,$location}
    """.trimIndent()

    private fun saveProfile(uid: String, body: String = profileJson()) = mvc.put("/api/v1/worker/profile") {
        with(token(uid))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    /** The first bytes of a real JPEG, enough for the content check. */
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(2_000) { 7 }

    private fun upload(uid: String, bytes: ByteArray = jpeg, type: String = "Aadhaar") =
        mvc.multipart("/api/v1/worker/profile/document") {
            with(token(uid))
            file(MockMultipartFile("image", "id.jpg", "image/jpeg", bytes))
            param("type", type)
        }

    @Test
    fun `only workers have a worker profile`() {
        val customer = onboard("Asha", "Customer")
        mvc.get("/api/v1/worker/profile") { with(token(customer)) }.andExpect { status { isForbidden() } }
        // Signed in but never onboarded: same.
        mvc.get("/api/v1/worker/profile") { with(token(newUid())) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `profile is validated like the app's form and the location is rounded to about 1 km`() {
        val ravi = onboard("Ravi", "Worker")
        mvc.get("/api/v1/worker/profile") { with(token(ravi)) }.andExpect { status { isNoContent() } }

        for (bad in listOf(
            profileJson(categories = """["astrologer"]"""),
            profileJson(price = 30_050),
            profileJson(languages = """["Klingon"]"""),
            profileJson(location = """"latitude":31.3"""),
            profileJson(location = """"latitude":95.0,"longitude":75.0"""),
        )) {
            saveProfile(ravi, bad).andExpect {
                status { isBadRequest() }
                jsonPath("$.field") { value("profile") }
            }
        }

        saveProfile(ravi).andExpect {
            status { isOk() }
            jsonPath("$.services[0].name") { value("Fan repair") }
            jsonPath("$.latitude") { value(31.31) }
            jsonPath("$.longitude") { value(75.59) }
            jsonPath("$.status") { value("NotSubmitted") }
            jsonPath("$.isAvailable") { value(true) }
        }
        mvc.put("/api/v1/worker/profile/availability") {
            with(token(ravi))
            contentType = MediaType.APPLICATION_JSON
            content = """{"available":false}"""
        }.andExpect { jsonPath("$.isAvailable") { value(false) } }
    }

    @Test
    fun `documents are checked by content and size, and need a profile first`() {
        val ravi = onboard("Ravi", "Worker")
        upload(ravi).andExpect { jsonPath("$.field") { value("no_profile") } }
        saveProfile(ravi)

        upload(ravi, bytes = "not really a photo".toByteArray()).andExpect { jsonPath("$.field") { value("document") } }
        upload(ravi, type = "LibraryCard").andExpect { jsonPath("$.field") { value("document") } }
        upload(ravi, bytes = jpeg + ByteArray(WorkerProfileRules.MAX_DOCUMENT_BYTES)).andExpect {
            jsonPath("$.field") { value("document_too_large") }
        }

        upload(ravi).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("Pending") }
            jsonPath("$.documentType") { value("Aadhaar") }
        }
    }

    @Test
    fun `an admin approves a worker, who then appears in the catalog`() {
        val ravi = onboard("Ravi Kumar", "Worker")
        saveProfile(ravi)
        upload(ravi)
        mvc.get("/api/v1/catalog") { with(token(ravi)) }.andExpect { jsonPath("$.workers[*].id") { value(not(hasItem(ravi))) } }

        // Ordinary users can't see the queue, the documents, or decide.
        mvc.get("/api/v1/admin/verifications") { with(token(ravi)) }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/admin/verifications/$ravi/document") { with(token(ravi)) }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/admin/verifications/$ravi/approve") { with(token(ravi)) }.andExpect { status { isForbidden() } }

        val admin = onboard("Meera Admin", "Customer").also(::makeAdmin)
        mvc.get("/api/v1/admin/verifications") { with(token(admin)) }.andExpect {
            status { isOk() }
            jsonPath("$[*].uid") { value(hasItem(ravi)) }
        }
        mvc.get("/api/v1/admin/verifications/$ravi/document") { with(token(admin)) }.andExpect {
            status { isOk() }
            content { contentType("image/jpeg") }
            header { string("Cache-Control", "no-store") }
        }

        mvc.post("/api/v1/admin/verifications/$ravi/approve") { with(token(admin)) }.andExpect { status { isOk() } }
        mvc.get("/api/v1/worker/profile") { with(token(ravi)) }.andExpect { jsonPath("$.status") { value("Verified") } }
        mvc.get("/api/v1/catalog") { with(token(admin)) }.andExpect {
            jsonPath("$.workers[?(@.id == '$ravi')].name") { value(hasItem("Ravi Kumar")) }
            jsonPath("$.categories.length()") { value(8) }
        }
        // Deciding twice is refused.
        mvc.post("/api/v1/admin/verifications/$ravi/approve") { with(token(admin)) }.andExpect { status { isConflict() } }
    }

    @Test
    fun `a rejected worker can submit again, and admins can't verify themselves`() {
        val ravi = onboard("Ravi", "Worker")
        saveProfile(ravi)
        upload(ravi)
        val admin = onboard("Meera", "Customer", "Worker").also(::makeAdmin)

        mvc.post("/api/v1/admin/verifications/$ravi/reject") {
            with(token(admin))
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"x"}"""
        }.andExpect { jsonPath("$.field") { value("reason") } }
        mvc.post("/api/v1/admin/verifications/$ravi/reject") {
            with(token(admin))
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"Photo is blurry, please retake it"}"""
        }.andExpect { status { isOk() } }
        mvc.get("/api/v1/worker/profile") { with(token(ravi)) }.andExpect { jsonPath("$.status") { value("Rejected") } }

        upload(ravi).andExpect { jsonPath("$.status") { value("Pending") } }

        saveProfile(admin)
        upload(admin)
        mvc.post("/api/v1/admin/verifications/$admin/approve") { with(token(admin)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `nearby finds verified workers within the radius, nearest first, by category`() {
        val admin = onboard("Meera", "Customer").also(::makeAdmin)
        // Somewhere no other test puts anyone (Shillong), so results only contain these two.
        val near = onboard("Near Worker", "Worker")
        val far = onboard("Far Worker", "Worker")
        saveProfile(near, profileJson(location = """"latitude":25.578,"longitude":91.893"""))
        saveProfile(far, profileJson(categories = """["plumber"]""", location = """"latitude":25.62,"longitude":91.95"""))
        for (uid in listOf(near, far)) {
            upload(uid)
            mvc.post("/api/v1/admin/verifications/$uid/approve") { with(token(admin)) }
        }

        mvc.get("/api/v1/workers/nearby?lat=25.5781&lng=91.8931&radiusKm=10") { with(token(admin)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(near) }
            jsonPath("$[1].id") { value(far) }
        }
        mvc.get("/api/v1/workers/nearby?lat=25.5781&lng=91.8931&radiusKm=2") { with(token(admin)) }.andExpect {
            jsonPath("$.length()") { value(1) }
        }
        mvc.get("/api/v1/workers/nearby?lat=25.5781&lng=91.8931&radiusKm=10&categoryId=plumber") { with(token(admin)) }
            .andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(far) }
            }
        mvc.get("/api/v1/workers/nearby?lat=25.5781&lng=91.8931&radiusKm=500") { with(token(admin)) }.andExpect {
            jsonPath("$.field") { value("radius") }
        }
    }
}
