package com.sahyog.core.data.network

import com.sahyog.core.common.result.AppError
import com.sahyog.core.common.result.AppResult
import com.sahyog.core.data.di.createSahyogApi
import com.sahyog.core.data.repository.RetrofitUserDataSource
import com.sahyog.core.data.worker.RetrofitWorkerDataSource
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The real HTTP client (Retrofit + OkHttp + our interceptors) against a local mock server. */
class SahyogApiClientTest {

    private val server = MockWebServer()
    private val tokens = object : IdTokenProvider {
        val forced = mutableListOf<Boolean>()
        override fun idToken(forceRefresh: Boolean): String {
            forced += forceRefresh
            return if (forceRefresh) "fresh-token" else "cached-token"
        }
    }
    private lateinit var api: SahyogApi

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenRefreshAuthenticator(tokens))
            .build()
        api = createSahyogApi(server.url("/").toString(), client)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private val user = """{"uid":"u1","phoneNumber":"+919876543210","name":"Asha","roles":["Customer"],"workerStatus":null}"""

    @Test
    fun `every request carries the Firebase token`() = runTest {
        respond(200, user)
        val me = RetrofitUserDataSource(api).fetchMe()
        assertEquals("Asha", me?.name)
        assertEquals("Bearer cached-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `an expired token is refreshed once and the request retried`() = runTest {
        respond(401, """{"code":"unauthorized"}""")
        respond(200, user)
        RetrofitUserDataSource(api).fetchMe()
        server.takeRequest()
        assertEquals("Bearer fresh-token", server.takeRequest().headers["Authorization"])
        assertEquals(listOf(false, true), tokens.forced)
    }

    @Test
    fun `a second 401 is final and becomes Unauthorized`() = runTest {
        respond(401)
        respond(401)
        assertEquals(AppResult.Failure(AppError.Unauthorized), apiCall { api.bookings() })
    }

    @Test
    fun `server refusals map to AppError with the field to fix`() = runTest {
        respond(400, """{"code":"validation","field":"slot_taken"}""")
        assertEquals(AppResult.Failure(AppError.Validation(field = "slot_taken")), apiCall { api.cancelBooking("SH-1") })
        respond(403, """{"code":"forbidden"}""")
        assertEquals(AppResult.Failure(AppError.Forbidden), apiCall { api.jobs() })
        respond(404)
        assertEquals(AppResult.Failure(AppError.NotFound), apiCall { api.cancelBooking("SH-1") })
        respond(503, "<html>down</html>")
        assertEquals(AppResult.Failure(AppError.Server(503)), apiCall { api.bookings() })
    }

    @Test
    fun `not onboarded and no worker profile are normal answers, not errors`() = runTest {
        respond(404, """{"code":"not_found"}""")
        assertNull(RetrofitUserDataSource(api).fetchMe())
        respond(204)
        assertNull(RetrofitWorkerDataSource(api).fetchMyProfile())
    }

    @Test
    fun `unknown fields from a newer server are ignored`() = runTest {
        respond(200, """{"categories":[],"workers":[],"somethingNew":42}""")
        assertTrue(api.catalog().workers.isEmpty())
    }

    @Test
    fun `ID photos are sent as multipart with the document type`() = runTest {
        respond(
            200,
            """{"categoryIds":["electrician"],"services":[],"about":"","locality":"Model Town","languages":["Hindi"],
               "yearsExperience":3,"isAvailable":true,"status":"Pending","documentType":"Aadhaar"}""",
        )
        val profile = RetrofitWorkerDataSource(api).submitIdDocument("Aadhaar", byteArrayOf(1, 2, 3), "image/jpeg")
        assertEquals("Pending", profile.status)
        val request = server.takeRequest()
        assertEquals("/api/v1/worker/profile/document", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue(body.contains("name=\"type\"") && body.contains("Aadhaar"))
        assertTrue(body.contains("name=\"image\"") && body.contains("Content-Type: image/jpeg"))
    }
}
