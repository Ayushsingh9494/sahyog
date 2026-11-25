package com.sahyog.server

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.util.UUID

/**
 * Base for API tests: a full server on a real PostGIS, with helpers to act as signed-in
 * people. Each test makes fresh users (random uids), so tests don't depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase::class)
abstract class ApiTest {

    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var jdbc: JdbcClient

    /** A signed-in caller with a phone-number Firebase token. */
    fun token(uid: String, phone: String? = "+919876543210") = jwt().jwt {
        it.subject(uid)
        if (phone != null) it.claim("phone_number", phone)
    }

    fun newUid(prefix: String = "u") = "$prefix-" + UUID.randomUUID()

    /** Onboards a new person with [roles] and returns their uid. */
    fun onboard(name: String, vararg roles: String): String {
        val uid = newUid(name.lowercase().substringBefore(' '))
        mvc.put("/api/v1/me") {
            with(token(uid))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","roles":[${roles.joinToString { "\"$it\"" }}]}"""
        }.andExpect { status { isOk() } }
        return uid
    }

    /** Admin rights are only ever granted in the database. */
    fun makeAdmin(uid: String) {
        jdbc.sql("UPDATE users SET is_admin = true WHERE uid = :uid").param("uid", uid).update()
    }
}
