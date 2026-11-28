package com.sahyog.server.payment

import com.sahyog.server.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** Stands in for Razorpay: tests decide what the gateway "says" about each payment. */
class FakeGateway : PaymentGateway {
    override var isEnabled = true
    override val publicKeyId = "rzp_test_fake"
    val payments = mutableMapOf<String, GatewayPayment>()
    var orders = 0
    var captured = mutableListOf<String>()

    override fun createOrder(amountPaise: Long, receipt: String) = GatewayOrder("order_${++orders}", amountPaise, "INR")

    override fun isSignatureValid(orderId: String, paymentId: String, signature: String) = signature == "sig-$orderId-$paymentId"

    override fun fetchPayment(paymentId: String) = payments[paymentId] ?: throw GatewayException("unknown")

    override fun capture(paymentId: String, amountPaise: Long): GatewayPayment {
        captured += paymentId
        return payments.getValue(paymentId).copy(status = "captured").also { payments[paymentId] = it }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class FakeGatewayConfig {
    @Bean
    @Primary
    fun fakeGateway() = FakeGateway()
}

@Import(FakeGatewayConfig::class)
class PaymentFlowTest : ApiTest() {

    @Autowired lateinit var gateway: FakeGateway

    @BeforeEach
    fun enable() {
        gateway.isEnabled = true
    }

    private fun ResultActionsDsl.field(name: String): String =
        andReturn().response.contentAsString.let { Regex("\"$name\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    /** Ravi completes Asha's booking (₹300) paid by [method]. Returns (asha, ravi, bookingId). */
    private fun completedBooking(method: String): Triple<String, String, String> {
        val ravi = verifiedWorker("Ravi")
        val asha = onboard("Asha", "Customer")
        val id = mvc.post("/api/v1/bookings") {
            with(token(asha))
            contentType = MediaType.APPLICATION_JSON
            content = """{"workerId":"$ravi","serviceName":"Fan repair","slotDate":"2026-11-23","slotStartHour":11,
                "address":"12 Main Road, Model Town","paymentMethod":"$method"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.paymentMethod") { value(method) }
            jsonPath("$.paymentStatus") { value("Unpaid") }
        }.field("id")
        mvc.post("/api/v1/worker/jobs/$id/accept") { with(token(ravi)) }
        val code = mvc.get("/api/v1/bookings") { with(token(asha)) }.field("startCode")
        mvc.post("/api/v1/worker/jobs/$id/start-trip") { with(token(ravi)) }
        mvc.post("/api/v1/worker/jobs/$id/start") {
            with(token(ravi))
            contentType = MediaType.APPLICATION_JSON
            content = """{"startCode":"$code"}"""
        }
        mvc.post("/api/v1/worker/jobs/$id/complete") { with(token(ravi)) }.andExpect { jsonPath("$.status") { value("Completed") } }
        return Triple(asha, ravi, id)
    }

    private fun verify(customer: String, id: String, orderId: String, paymentId: String, signature: String) =
        mvc.post("/api/v1/bookings/$id/payment/verify") {
            with(token(customer))
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderId":"$orderId","paymentId":"$paymentId","signature":"$signature"}"""
        }

    @Test
    fun `online payment after the job, checked with the gateway before it counts`() {
        val (asha, _, id) = completedBooking("Online")
        val orderId = mvc.post("/api/v1/bookings/$id/payment/order") { with(token(asha)) }.andExpect {
            status { isOk() }
            jsonPath("$.amountPaise") { value(30_000) }
            jsonPath("$.keyId") { value("rzp_test_fake") }
        }.field("orderId")
        // Asking again re-uses the same order.
        mvc.post("/api/v1/bookings/$id/payment/order") { with(token(asha)) }.andExpect { jsonPath("$.orderId") { value(orderId) } }

        gateway.payments["pay_1"] = GatewayPayment("pay_1", orderId, 30_000, "captured")
        verify(asha, id, orderId, "pay_1", "forged").andExpect { jsonPath("$.field") { value("payment_failed") } }
        verify(asha, id, orderId, "pay_1", "sig-$orderId-pay_1").andExpect {
            status { isOk() }
            jsonPath("$.paymentStatus") { value("Paid") }
            jsonPath("$.paidVia") { value("Online") }
        }
        // A retried confirmation gets the same answer; paying twice is refused.
        verify(asha, id, orderId, "pay_1", "sig-$orderId-pay_1").andExpect { status { isOk() } }
        mvc.post("/api/v1/bookings/$id/payment/order") { with(token(asha)) }.andExpect { jsonPath("$.field") { value("payment") } }
    }

    @Test
    fun `the gateway record must match the amount, the order and be captured`() {
        val (asha, _, id) = completedBooking("Online")
        val orderId = mvc.post("/api/v1/bookings/$id/payment/order") { with(token(asha)) }.field("orderId")

        gateway.payments["pay_cheap"] = GatewayPayment("pay_cheap", orderId, 100, "captured")
        verify(asha, id, orderId, "pay_cheap", "sig-$orderId-pay_cheap").andExpect { jsonPath("$.field") { value("payment_failed") } }

        gateway.payments["pay_other"] = GatewayPayment("pay_other", "order_elsewhere", 30_000, "captured")
        verify(asha, id, orderId, "pay_other", "sig-$orderId-pay_other").andExpect { jsonPath("$.field") { value("payment_failed") } }

        gateway.payments["pay_failed"] = GatewayPayment("pay_failed", orderId, 30_000, "failed")
        verify(asha, id, orderId, "pay_failed", "sig-$orderId-pay_failed").andExpect { jsonPath("$.field") { value("payment_failed") } }

        // Authorised but not yet captured: the server captures it, then records it.
        gateway.payments["pay_auth"] = GatewayPayment("pay_auth", orderId, 30_000, "authorized")
        verify(asha, id, orderId, "pay_auth", "sig-$orderId-pay_auth").andExpect { jsonPath("$.paymentStatus") { value("Paid") } }
        assert("pay_auth" in gateway.captured)
    }

    @Test
    fun `cash on delivery is confirmed by the worker, after the job, for their own job only`() {
        val (asha, ravi, id) = completedBooking("Cash")
        val other = verifiedWorker("Other Worker")
        mvc.post("/api/v1/worker/jobs/$id/cash-received") { with(token(other)) }.andExpect { status { isNotFound() } }
        mvc.post("/api/v1/worker/jobs/$id/cash-received") { with(token(ravi)) }.andExpect {
            jsonPath("$.paymentStatus") { value("Paid") }
            jsonPath("$.paidVia") { value("Cash") }
        }
        mvc.post("/api/v1/worker/jobs/$id/cash-received") { with(token(ravi)) }.andExpect { jsonPath("$.field") { value("payment") } }
        mvc.get("/api/v1/bookings") { with(token(asha)) }.andExpect { jsonPath("$[0].paymentStatus") { value("Paid") } }

        val admin = onboard("Admin", "Customer").also(::makeAdmin)
        mvc.get("/api/v1/admin/overview") { with(token(admin)) }.andExpect {
            jsonPath("$.collectedCashPaise") { value(org.hamcrest.Matchers.greaterThanOrEqualTo(30_000)) }
        }
    }

    @Test
    fun `nothing can be paid before the job is completed, and online needs the gateway`() {
        val ravi = verifiedWorker("Ravi")
        val asha = onboard("Asha", "Customer")
        val id = mvc.post("/api/v1/bookings") {
            with(token(asha))
            contentType = MediaType.APPLICATION_JSON
            content = """{"workerId":"$ravi","serviceName":"Fan repair","slotDate":"2026-11-23","slotStartHour":13,
                "address":"12 Main Road, Model Town","paymentMethod":"Online"}"""
        }.field("id")
        mvc.post("/api/v1/bookings/$id/payment/order") { with(token(asha)) }.andExpect { jsonPath("$.field") { value("payment") } }
        mvc.post("/api/v1/worker/jobs/$id/cash-received") { with(token(ravi)) }.andExpect { jsonPath("$.field") { value("payment") } }

        gateway.isEnabled = false
        mvc.get("/api/v1/payments/config") { with(token(asha)) }.andExpect { jsonPath("$.onlineEnabled") { value(false) } }
        mvc.post("/api/v1/bookings") {
            with(token(asha))
            contentType = MediaType.APPLICATION_JSON
            content = """{"workerId":"$ravi","serviceName":"Fan repair","slotDate":"2026-11-23","slotStartHour":15,
                "address":"12 Main Road, Model Town","paymentMethod":"Online"}"""
        }.andExpect { jsonPath("$.field") { value("payment") } }
    }
}
