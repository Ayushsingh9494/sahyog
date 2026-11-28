package com.sahyog.core.testing

import com.sahyog.core.data.repository.PaymentCheckout
import com.sahyog.core.model.CheckoutResult
import com.sahyog.core.model.PaymentOrder

/** Stands in for the gateway's checkout screen: returns [result] and records what it was asked. */
class TestPaymentCheckout : PaymentCheckout {
    var result: CheckoutResult = CheckoutResult.Completed(orderId = "order_test", paymentId = "pay_test", signature = "sig")
    val opened = mutableListOf<Pair<PaymentOrder, String?>>()

    override suspend fun pay(order: PaymentOrder, phoneNumber: String?): CheckoutResult {
        opened += order to phoneNumber
        return result
    }
}
