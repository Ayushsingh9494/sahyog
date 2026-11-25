package com.sahyog.server.booking

import com.sahyog.server.ApiTest
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The clock starts at Monday 23 Nov 2026, 8:00 AM (see [ApiTest]). */
class BookingFlowTest : ApiTest() {

    private fun request(
        worker: String,
        service: String = "Fan repair",
        date: String = "2026-11-23",
        hour: Int = 11,
        address: String = "  12 Main Road, Model Town ",
        extra: String = "",
    ) = """{"workerId":"$worker","serviceName":"$service","slotDate":"$date","slotStartHour":$hour,
            "address":"$address","note":"  "$extra}"""

    private fun book(customer: String, body: String) = mvc.post("/api/v1/bookings") {
        with(token(customer))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun ResultActionsDsl.id(): String =
        andReturn().response.contentAsString.let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    private fun job(worker: String, id: String, action: String, body: String? = null) =
        mvc.post("/api/v1/worker/jobs/$id/$action") {
            with(token(worker))
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }

    private fun customerView(customer: String, id: String) =
        mvc.get("/api/v1/bookings") { with(token(customer)) }.andReturn().response.contentAsString
            .let { all -> Regex("\\{[^{}]*\"id\":\"$id\"[^{}]*(\\{[^{}]*\\}[^{}]*)?\\}").find(all)!!.value }

    private fun startCode(customer: String, id: String): String =
        Regex("\"startCode\":\"(\\d{4})\"").find(customerView(customer, id))!!.groupValues[1]

    @Test
    fun `a booking takes the server's price and the customer's name, with input cleaned up`() {
        val ravi = verifiedWorker("Ravi Kumar")
        val asha = onboard("Asha Devi", "Customer")
        book(asha, request(ravi, extra = ""","latitude":31.3085123456,"longitude":75.5871987654""")).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("Requested") }
            jsonPath("$.pricePaise") { value(30_000) }
            jsonPath("$.address") { value("12 Main Road, Model Town") }
            jsonPath("$.note") { value(nullValue()) }
            jsonPath("$.customerName") { value("Asha Devi") }
            jsonPath("$.workerName") { value("Ravi Kumar") }
            jsonPath("$.latitude") { value(31.30851) }
            jsonPath("$.id") { value(org.hamcrest.Matchers.matchesPattern("SH-[2-9A-HJ-NP-Z]{6}")) }
        }
    }

    @Test
    fun `bad requests are refused with the field to fix`() {
        clock.set(LocalDateTime.of(2026, 11, 23, 8, 30))
        val ravi = verifiedWorker("Ravi", "Customer", "Worker")
        val asha = onboard("Asha", "Customer")
        val cases = mapOf(
            request("nobody") to "worker",
            request(ravi, service = "Painting") to "service",
            request(ravi, hour = 7) to "slot",
            request(ravi, hour = 9) to "slot", // starts in 30 minutes: less than the 60-minute lead
            request(ravi, date = "2026-11-30") to "slot", // 8th day ahead
            request(ravi, date = "tomorrow") to "slot",
            request(ravi, address = "Flat 2") to "address",
            request(ravi, extra = ""","latitude":31.3""") to "location",
            request(ravi, extra = ""","latitude":95.0,"longitude":75.0""") to "location",
        )
        cases.forEach { (body, field) ->
            book(asha, body).andExpect {
                status { isBadRequest() }
                jsonPath("$.field") { value(field) }
            }
        }
        // Workers can't book themselves.
        book(ravi, request(ravi)).andExpect { jsonPath("$.field") { value("worker") } }
        // A worker who switched off "Accepting jobs" can't be booked.
        mvc.put("/api/v1/worker/profile/availability") {
            with(token(ravi))
            contentType = MediaType.APPLICATION_JSON
            content = """{"available":false}"""
        }
        book(asha, request(ravi)).andExpect { jsonPath("$.field") { value("worker") } }
        // Only customers book.
        val workerOnly = onboard("Gita", "Worker")
        book(workerOnly, request(ravi)).andExpect { status { isForbidden() } }
    }

    @Test
    fun `a worker can't be double booked, even by another customer`() {
        val ravi = verifiedWorker("Ravi")
        book(onboard("Asha", "Customer"), request(ravi)).andExpect { status { isCreated() } }
        book(onboard("Bina", "Customer"), request(ravi)).andExpect { jsonPath("$.field") { value("slot_taken") } }
        book(onboard("Bina", "Customer"), request(ravi, hour = 13)).andExpect { status { isCreated() } }
    }

    @Test
    fun `the full job lifecycle, and the worker never sees the start code`() {
        val ravi = verifiedWorker("Ravi")
        val asha = onboard("Asha Devi", "Customer")
        val id = book(asha, request(ravi, date = "2026-11-24")).id()

        mvc.get("/api/v1/worker/jobs") { with(token(ravi)) }.andExpect {
            jsonPath("$[0].customerName") { value("Asha Devi") }
            jsonPath("$[0].status") { value("Requested") }
        }
        job(ravi, id, "accept").andExpect {
            jsonPath("$.status") { value("Accepted") }
            jsonPath("$.startCode") { value(nullValue()) }
        }
        val code = startCode(asha, id)

        job(ravi, id, "start", """{"startCode":"$code"}""").andExpect { jsonPath("$.field") { value("status") } }
        // The visit is tomorrow at 11: setting off today is too early; 3 hours before is fine.
        job(ravi, id, "start-trip").andExpect { jsonPath("$.field") { value("too_early") } }
        clock.set(LocalDateTime.of(2026, 11, 24, 8, 0))
        job(ravi, id, "start-trip").andExpect { jsonPath("$.status") { value("WorkerEnRoute") } }

        val wrong = if (code == "1111") "2222" else "1111"
        job(ravi, id, "start", """{"startCode":"$wrong"}""").andExpect { jsonPath("$.field") { value("start_code") } }
        job(ravi, id, "start", """{"startCode":" $code "}""").andExpect { jsonPath("$.status") { value("InProgress") } }
        clock.advanceMinutes(90)
        job(ravi, id, "complete").andExpect {
            jsonPath("$.status") { value("Completed") }
            jsonPath("$.completedAtMillis") { value(clock.millis()) }
        }
        assert(customerView(asha, id).contains("\"status\":\"Completed\""))
    }

    @Test
    fun `customers cancel only before the worker sets off, and declined requests stay declined`() {
        // 10:30: a 1 PM visit can be booked, and its trip may start (from 10 AM).
        clock.set(LocalDateTime.of(2026, 11, 23, 10, 30))
        val ravi = verifiedWorker("Ravi")
        val asha = onboard("Asha", "Customer")
        val first = book(asha, request(ravi, hour = 13)).id()
        job(ravi, first, "accept")
        mvc.post("/api/v1/bookings/$first/cancel") { with(token(asha)) }.andExpect {
            jsonPath("$.status") { value("Cancelled") }
        }
        job(ravi, first, "start-trip").andExpect { jsonPath("$.field") { value("status") } }

        // The cancelled booking freed the slot.
        val second = book(asha, request(ravi, hour = 13)).id()
        job(ravi, second, "accept")
        job(ravi, second, "start-trip").andExpect { jsonPath("$.status") { value("WorkerEnRoute") } }
        mvc.post("/api/v1/bookings/$second/cancel") { with(token(asha)) }.andExpect { jsonPath("$.field") { value("status") } }

        val third = book(asha, request(ravi, hour = 15)).id()
        job(ravi, third, "decline").andExpect { jsonPath("$.status") { value("Rejected") } }
        job(ravi, third, "accept").andExpect { jsonPath("$.field") { value("status") } }
    }

    @Test
    fun `unanswered requests expire when the visit time arrives`() {
        val ravi = verifiedWorker("Ravi")
        val asha = onboard("Asha", "Customer")
        val id = book(asha, request(ravi)).id()
        clock.set(LocalDateTime.of(2026, 11, 23, 10, 59))
        assert(customerView(asha, id).contains("\"status\":\"Requested\""))
        clock.set(LocalDateTime.of(2026, 11, 23, 11, 0))
        assert(customerView(asha, id).contains("\"status\":\"Expired\""))
        job(ravi, id, "accept").andExpect { jsonPath("$.field") { value("status") } }
    }

    @Test
    fun `nobody sees or touches other people's bookings`() {
        val ravi = verifiedWorker("Ravi")
        val other = verifiedWorker("Other Worker")
        val asha = onboard("Asha", "Customer")
        val stranger = onboard("Stranger", "Customer")
        val id = book(asha, request(ravi)).id()

        mvc.get("/api/v1/bookings") { with(token(stranger)) }.andExpect { jsonPath("$.length()") { value(0) } }
        mvc.post("/api/v1/bookings/$id/cancel") { with(token(stranger)) }.andExpect { status { isNotFound() } }
        job(other, id, "accept").andExpect { status { isNotFound() } }
        mvc.get("/api/v1/worker/jobs") { with(token(asha)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a completed job is reviewed once, shows publicly by first name, and sets the rating`() {
        val ravi = verifiedWorker("Ravi Kumar")
        val asha = onboard("Asha Devi", "Customer")
        val id = book(asha, request(ravi)).id()
        val review = { rating: Int, comment: String ->
            mvc.post("/api/v1/bookings/$id/review") {
                with(token(asha))
                contentType = MediaType.APPLICATION_JSON
                content = """{"rating":$rating,"comment":"$comment"}"""
            }
        }
        review(5, "Too soon").andExpect { jsonPath("$.field") { value("review_closed") } }

        job(ravi, id, "accept")
        job(ravi, id, "start-trip")
        job(ravi, id, "start", """{"startCode":"${startCode(asha, id)}"}""")
        job(ravi, id, "complete")

        review(6, "").andExpect { jsonPath("$.field") { value("review") } }
        review(4, "x".repeat(501)).andExpect { jsonPath("$.field") { value("review") } }
        review(4, "  Fixed it quickly.  ").andExpect {
            status { isOk() }
            jsonPath("$.review.rating") { value(4) }
            jsonPath("$.review.comment") { value("Fixed it quickly.") }
        }
        review(1, "Changed my mind").andExpect { jsonPath("$.field") { value("review_closed") } }

        mvc.get("/api/v1/workers/$ravi/reviews") { with(token(asha)) }.andExpect {
            jsonPath("$[0].authorName") { value("Asha") }
            jsonPath("$[0].serviceName") { value("Fan repair") }
        }
        mvc.get("/api/v1/catalog") { with(token(asha)) }.andExpect {
            jsonPath("$.workers[?(@.id == '$ravi')].rating") { value(hasItem(4.0)) }
            jsonPath("$.workers[?(@.id == '$ravi')].ratingCount") { value(hasItem(1)) }
            jsonPath("$.workers[?(@.id == '$ravi')].jobsCompleted") { value(hasItem(1)) }
        }
        mvc.get("/api/v1/worker/jobs") { with(token(ravi)) }.andExpect { jsonPath("$[0].review.rating") { value(4) } }
    }

    @Test
    fun `reviews close 14 days after completion`() {
        val ravi = verifiedWorker("Ravi")
        val asha = onboard("Asha", "Customer")
        val id = book(asha, request(ravi)).id()
        job(ravi, id, "accept")
        job(ravi, id, "start-trip")
        job(ravi, id, "start", """{"startCode":"${startCode(asha, id)}"}""")
        job(ravi, id, "complete")
        clock.advanceMinutes(14 * 24 * 60)
        mvc.post("/api/v1/bookings/$id/review") {
            with(token(asha))
            contentType = MediaType.APPLICATION_JSON
            content = """{"rating":5}"""
        }.andExpect { jsonPath("$.field") { value("review_closed") } }
        assertNotNull(id)
        assertEquals(true, id.startsWith("SH-"))
    }
}
