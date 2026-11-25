package com.sahyog.server.admin

import com.sahyog.server.ApiTest
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class AdminConsoleTest : ApiTest() {

    /** "+910000099999" is in `sahyog.admin.phone-numbers` (see [ApiTest]). */
    private fun configuredAdmin(): String {
        val uid = newUid("staff")
        mvc.put("/api/v1/me") {
            with(token(uid, phone = "+910000099999"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Meera Staff","roles":["Customer"]}"""
        }.andExpect { jsonPath("$.isAdmin") { value(true) } }
        return uid
    }

    private fun pendingWorker(name: String): String {
        val uid = onboard(name, "Worker")
        saveProfile(uid)
        upload(uid)
        return uid
    }

    @Test
    fun `staff numbers from the server config become admins, others don't`() {
        val admin = configuredAdmin()
        mvc.get("/api/v1/me") { with(token(admin, phone = "+910000099999")) }.andExpect {
            jsonPath("$.isAdmin") { value(true) }
        }
        val asha = onboard("Asha", "Customer")
        mvc.get("/api/v1/me") { with(token(asha)) }.andExpect { jsonPath("$.isAdmin") { value(false) } }
        mvc.get("/api/v1/admin/overview") { with(token(asha)) }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/admin/workers") { with(token(asha)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the queue shows who is waiting with their area, and the overview counts them`() {
        val admin = configuredAdmin()
        val ravi = pendingWorker("Ravi Kumar")
        mvc.get("/api/v1/admin/verifications") { with(token(admin)) }.andExpect {
            jsonPath("$[?(@.uid == '$ravi')].locality") { value(hasItem("Model Town")) }
            jsonPath("$[?(@.uid == '$ravi')].categoryIds[0]") { value(hasItem("electrician")) }
            jsonPath("$[?(@.uid == '$ravi')].isResubmission") { value(hasItem(false)) }
        }
        mvc.get("/api/v1/admin/overview") { with(token(admin)) }.andExpect {
            status { isOk() }
            jsonPath("$.pendingVerifications") { value(greaterThanOrEqualTo(1)) }
        }
    }

    @Test
    fun `decisions are recorded with who made them and why, and show in the worker's history`() {
        val admin = configuredAdmin()
        val ravi = pendingWorker("Ravi Kumar")
        mvc.post("/api/v1/admin/verifications/$ravi/reject") {
            with(token(admin))
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"Photo is blurry"}"""
        }
        upload(ravi)
        mvc.get("/api/v1/admin/verifications") { with(token(admin)) }.andExpect {
            jsonPath("$[?(@.uid == '$ravi')].isResubmission") { value(hasItem(true)) }
        }
        mvc.post("/api/v1/admin/verifications/$ravi/approve") { with(token(admin)) }

        mvc.get("/api/v1/admin/workers/$ravi") { with(token(admin)) }.andExpect {
            status { isOk() }
            jsonPath("$.summary.status") { value("Verified") }
            jsonPath("$.summary.phoneNumber") { value("+919876543210") }
            jsonPath("$.services[0].name") { value("Fan repair") }
            jsonPath("$.documentType") { value("Aadhaar") }
            // Newest first: approved, re-submitted, rejected (with reason), first submission.
            jsonPath("$.history[*].toStatus") { value(org.hamcrest.Matchers.contains("Verified", "Pending", "Rejected", "Pending")) }
            jsonPath("$.history[0].changedByName") { value("Meera Staff") }
            jsonPath("$.history[2].reason") { value("Photo is blurry") }
            jsonPath("$.history[3].changedByName") { value(org.hamcrest.Matchers.nullValue()) }
        }
        mvc.get("/api/v1/admin/overview") { with(token(admin)) }.andExpect {
            jsonPath("$.recentDecisions[*].workerUid") { value(hasItem(ravi)) }
        }
    }

    @Test
    fun `the directory filters by status and searches name, phone or area`() {
        val admin = configuredAdmin()
        val pending = pendingWorker("Zubair Pending")
        val verified = verifiedWorker("Yusuf Verified")
        mvc.get("/api/v1/admin/workers?status=Pending") { with(token(admin)) }.andExpect {
            jsonPath("$[*].uid") { value(hasItem(pending)) }
            jsonPath("$[*].uid") { value(not(hasItem(verified))) }
            jsonPath("$[*].status") { value(everyItem(`is`("Pending"))) }
        }
        mvc.get("/api/v1/admin/workers?q=yusuf") { with(token(admin)) }.andExpect {
            jsonPath("$[*].uid") { value(hasItem(verified)) }
            jsonPath("$[*].uid") { value(not(hasItem(pending))) }
        }
        mvc.get("/api/v1/admin/workers?status=Bogus") { with(token(admin)) }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `revoking takes a verified worker out of the catalog, with a reason`() {
        val admin = configuredAdmin()
        val ravi = verifiedWorker("Ravi Kumar")
        val revoke = { reason: String ->
            mvc.post("/api/v1/admin/workers/$ravi/revoke") {
                with(token(admin))
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"$reason"}"""
            }
        }
        revoke("x").andExpect { jsonPath("$.field") { value("reason") } }
        revoke("Repeated no-shows reported by customers").andExpect { status { isOk() } }
        mvc.get("/api/v1/catalog") { with(token(admin)) }.andExpect {
            jsonPath("$.workers[*].id") { value(not(hasItem(ravi))) }
        }
        revoke("Again").andExpect { status { isConflict() } }
        mvc.get("/api/v1/admin/workers/$ravi") { with(token(admin)) }.andExpect {
            jsonPath("$.history[0].reason") { value("Repeated no-shows reported by customers") }
        }
    }
}
