package com.sahyog.server.user

import com.sahyog.server.ApiTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

class MeControllerTest : ApiTest() {

    private fun putProfile(uid: String, body: String, phone: String? = "+919876543210") =
        mvc.put("/api/v1/me") {
            with(token(uid, phone))
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    @Test
    fun `health is public, the API is not`() {
        mvc.get("/actuator/health").andExpect { status { isOk() } }
        mvc.get("/api/v1/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("unauthorized") }
        }
    }

    @Test
    fun `a new user is not found until onboarding, then gets their profile back`() {
        val uid = newUid()
        mvc.get("/api/v1/me") { with(token(uid)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("not_found") }
        }

        putProfile(uid, """{"name":"  Asha Devi ","roles":["Customer","Worker"]}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Asha Devi") }
            jsonPath("$.phoneNumber") { value("+919876543210") }
            jsonPath("$.workerStatus") { value("NotSubmitted") }
        }

        mvc.get("/api/v1/me") { with(token(uid)) }.andExpect {
            status { isOk() }
            jsonPath("$.roles.length()") { value(2) }
        }
    }

    @Test
    fun `invalid input is refused with the offending field`() {
        val uid = newUid()
        putProfile(uid, """{"name":"A","roles":["Customer"]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.field") { value("name") }
        }
        putProfile(uid, """{"name":"Asha <script>","roles":["Customer"]}""").andExpect {
            jsonPath("$.field") { value("name") }
        }
        putProfile(uid, """{"name":"Asha","roles":[]}""").andExpect {
            jsonPath("$.field") { value("roles") }
        }
        putProfile(uid, """{"name":"Asha","roles":["Admin"]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("validation") }
        }
        putProfile(uid, """{"name":"Asha","roles":["Customer"]}""", phone = null).andExpect {
            jsonPath("$.field") { value("phone") }
        }
        mvc.get("/api/v1/me") { with(token(uid)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `roles can be added later but not dropped, and verification status is kept`() {
        val uid = newUid()
        putProfile(uid, """{"name":"Ravi","roles":["Worker"]}""")
        putProfile(uid, """{"name":"Ravi Kumar","roles":["Customer"]}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Ravi Kumar") }
            jsonPath("$.roles.length()") { value(2) }
            jsonPath("$.workerStatus") { value("NotSubmitted") }
        }
    }

    @Test
    fun `unknown endpoints and methods use the same error shape`() {
        mvc.get("/api/v1/nothing-here") { with(token(newUid())) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("not_found") }
        }
        mvc.get("/somewhere-else") { with(token(newUid())) }.andExpect { status { isForbidden() } }
    }
}
