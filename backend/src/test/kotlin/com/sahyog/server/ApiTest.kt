package com.sahyog.server

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** A clock tests can set and move. */
class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun set(local: LocalDateTime) {
        now = local.atZone(zone).toInstant()
    }

    fun advanceMinutes(minutes: Long) {
        now = now.plusSeconds(minutes * 60)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfig {
    @Bean
    @Primary
    fun testClock() = MutableClock(Instant.parse("2026-11-23T02:30:00Z"), ZoneId.of("Asia/Kolkata"))
}

/**
 * Base for API tests: a full server on a real PostGIS, with helpers to act as signed-in
 * people. Each test makes fresh users (random uids), so tests don't depend on each other.
 */
@SpringBootTest(
    properties = [
        "sahyog.storage.documents-dir=build/test-documents",
        "sahyog.admin.phone-numbers=+910000099999, +910000099998",
    ],
)
@AutoConfigureMockMvc
@Import(TestDatabase::class, TestClockConfig::class)
abstract class ApiTest {

    @Autowired lateinit var mvc: MockMvc

    @Autowired lateinit var jdbc: JdbcClient

    @Autowired lateinit var clock: MutableClock

    /** Every test starts on Monday 23 Nov 2026, 8:00 AM India time. */
    @BeforeEach
    fun resetClock() = clock.set(LocalDateTime.of(2026, 11, 23, 8, 0))

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

    fun profileJson(
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

    fun saveProfile(uid: String, body: String = profileJson()) = mvc.put("/api/v1/worker/profile") {
        with(token(uid))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    /** The first bytes of a real JPEG, enough for the content check. */
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(2_000) { 7 }

    fun upload(uid: String, bytes: ByteArray = jpeg, type: String = "Aadhaar") =
        mvc.multipart("/api/v1/worker/profile/document") {
            with(token(uid))
            file(MockMultipartFile("image", "id.jpg", "image/jpeg", bytes))
            param("type", type)
        }

    /** A worker who completed the profile and was approved by an admin. */
    fun verifiedWorker(name: String, vararg roles: String = arrayOf("Worker")): String {
        val uid = onboard(name, *roles)
        saveProfile(uid).andExpect { status { isOk() } }
        upload(uid).andExpect { status { isOk() } }
        val admin = onboard("Admin Person", "Customer").also(::makeAdmin)
        mvc.post("/api/v1/admin/verifications/$uid/approve") { with(token(admin)) }.andExpect { status { isOk() } }
        return uid
    }
}
