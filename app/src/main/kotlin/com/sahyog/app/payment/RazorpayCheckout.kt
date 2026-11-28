package com.sahyog.app.payment

import android.app.Activity
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.sahyog.core.data.repository.PaymentCheckout
import com.sahyog.core.model.CheckoutResult
import com.sahyog.core.model.PaymentOrder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens Razorpay's checkout over the app and suspends until the customer finishes.
 *
 * The SDK reports back to the activity that opened it, so [MainActivity][com.sahyog.app.MainActivity]
 * attaches itself here and forwards the result. Nothing here is trusted: the server checks the
 * result with Razorpay before the booking counts as paid.
 */
@Singleton
class RazorpayCheckout @Inject constructor() : PaymentCheckout, PaymentResultWithDataListener {

    private var activity: WeakReference<Activity>? = null
    private var pending: CompletableDeferred<CheckoutResult>? = null

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
        // Warms up the checkout so it opens quickly later.
        Checkout.preload(activity.applicationContext)
    }

    fun detach(activity: Activity) {
        if (this.activity?.get() === activity) this.activity = null
    }

    override suspend fun pay(order: PaymentOrder, phoneNumber: String?): CheckoutResult = withContext(Dispatchers.Main) {
        val host = activity?.get() ?: return@withContext CheckoutResult.Failed("No screen to open the checkout on")
        pending?.complete(CheckoutResult.Cancelled)
        val result = CompletableDeferred<CheckoutResult>().also { pending = it }
        try {
            Checkout().apply { setKeyID(order.keyId) }.open(host, order.toOptions(phoneNumber))
        } catch (e: Exception) {
            result.complete(CheckoutResult.Failed(e.message))
        }
        result.await().also { if (pending === result) pending = null }
    }

    override fun onPaymentSuccess(paymentId: String?, data: PaymentData?) {
        val orderId = data?.orderId
        val signature = data?.signature
        val id = paymentId ?: data?.paymentId
        pending?.complete(
            if (orderId != null && signature != null && id != null) {
                CheckoutResult.Completed(orderId = orderId, paymentId = id, signature = signature)
            } else {
                CheckoutResult.Failed("Incomplete checkout result")
            },
        )
    }

    override fun onPaymentError(code: Int, description: String?, data: PaymentData?) {
        pending?.complete(if (code == Checkout.PAYMENT_CANCELED) CheckoutResult.Cancelled else CheckoutResult.Failed(description))
    }

    private fun PaymentOrder.toOptions(phoneNumber: String?) = JSONObject().apply {
        put("name", "Sahyog")
        put("description", description)
        put("order_id", orderId)
        put("currency", "INR")
        put("amount", amount.value)
        // Pre-fills the customer's number (already verified at sign-in) so they type less.
        phoneNumber?.let { put("prefill", JSONObject().put("contact", it)) }
        put("theme", JSONObject().put("color", "#0B6B58"))
        put("retry", JSONObject().put("enabled", true).put("max_count", 3))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PaymentModule {
    @Binds
    abstract fun bindCheckout(checkout: RazorpayCheckout): PaymentCheckout
}
