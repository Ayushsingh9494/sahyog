package com.sahyog.server.payment

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A payment order the customer's app pays with the gateway's checkout. */
data class GatewayOrder(val id: String, val amountPaise: Long, val currency: String)

/** What the gateway itself says about a payment (never trusted from the app). */
data class GatewayPayment(val id: String, val orderId: String?, val amountPaise: Long, val status: String)

/**
 * The online payment provider (Razorpay). Behind an interface so tests use a fake and so the
 * server runs, with online payment switched off, when no keys are configured.
 */
interface PaymentGateway {
    /** False when no keys are configured: online payment is offered nowhere. */
    val isEnabled: Boolean

    /** The public key id the app's checkout needs. Never the secret. */
    val publicKeyId: String

    fun createOrder(amountPaise: Long, receipt: String): GatewayOrder

    /** The checkout's signature: proves the result came from the gateway for this order. */
    fun isSignatureValid(orderId: String, paymentId: String, signature: String): Boolean

    fun fetchPayment(paymentId: String): GatewayPayment

    /** Captures an authorised payment (when the account doesn't capture automatically). */
    fun capture(paymentId: String, amountPaise: Long): GatewayPayment
}

class GatewayException(message: String) : RuntimeException(message)

/**
 * Razorpay over its REST API (https://api.razorpay.com/v1), authenticated with the key id and
 * secret. The secret comes from server configuration only (`sahyog.razorpay.key-secret`: a
 * git-ignored local file, or Secret Manager in the cloud) and never reaches the app.
 */
@Component
class RazorpayGateway(
    @Value("\${sahyog.razorpay.key-id:}") private val keyId: String,
    @Value("\${sahyog.razorpay.key-secret:}") private val keySecret: String,
    private val json: JsonMapper,
) : PaymentGateway {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override val isEnabled: Boolean = keyId.isNotBlank() && keySecret.isNotBlank()

    override val publicKeyId: String get() = keyId

    override fun createOrder(amountPaise: Long, receipt: String): GatewayOrder {
        val body = json.writeValueAsString(mapOf("amount" to amountPaise, "currency" to CURRENCY, "receipt" to receipt))
        val node = call(HttpRequest.newBuilder(URI.create("$API/orders")).POST(HttpRequest.BodyPublishers.ofString(body)))
        return GatewayOrder(node.get("id").asString(), node.get("amount").asLong(), node.get("currency").asString())
    }

    override fun isSignatureValid(orderId: String, paymentId: String, signature: String): Boolean {
        if (!isEnabled) return false
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(keySecret.toByteArray(), "HmacSHA256")) }
        val expected = mac.doFinal("$orderId|$paymentId".toByteArray()).joinToString("") { "%02x".format(it) }
        // Constant-time comparison, so the check leaks nothing through timing.
        return MessageDigest.isEqual(expected.toByteArray(), signature.trim().lowercase().toByteArray())
    }

    override fun fetchPayment(paymentId: String): GatewayPayment =
        call(HttpRequest.newBuilder(URI.create("$API/payments/${safe(paymentId)}")).GET()).toPayment()

    override fun capture(paymentId: String, amountPaise: Long): GatewayPayment {
        val body = json.writeValueAsString(mapOf("amount" to amountPaise, "currency" to CURRENCY))
        return call(
            HttpRequest.newBuilder(URI.create("$API/payments/${safe(paymentId)}/capture"))
                .POST(HttpRequest.BodyPublishers.ofString(body)),
        ).toPayment()
    }

    private fun call(builder: HttpRequest.Builder): JsonNode {
        if (!isEnabled) throw GatewayException("Online payment is not configured")
        val auth = Base64.getEncoder().encodeToString("$keyId:$keySecret".toByteArray())
        val request = builder
            .header("Authorization", "Basic $auth")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        // The gateway's error text is logged by the caller's handler, never sent to the app.
        if (response.statusCode() !in 200..299) throw GatewayException("Gateway HTTP ${response.statusCode()}")
        return json.readTree(response.body())
    }

    private fun JsonNode.toPayment() = GatewayPayment(
        id = get("id").asString(),
        orderId = get("order_id")?.takeUnless { it.isNull }?.asString(),
        amountPaise = get("amount").asLong(),
        status = get("status").asString(),
    )

    /** Payment ids are "pay_" + letters and digits; anything else never reaches a URL. */
    private fun safe(id: String): String {
        require(id.matches(Regex("^[A-Za-z0-9_]{1,64}$"))) { "Bad payment id" }
        return id
    }

    private companion object {
        const val API = "https://api.razorpay.com/v1"
        const val CURRENCY = "INR"
    }
}
