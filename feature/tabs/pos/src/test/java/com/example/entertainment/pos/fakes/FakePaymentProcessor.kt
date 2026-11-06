package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.domain.PaymentProcessor
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentRequest
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.RefundResult
import kotlinx.coroutines.CompletableDeferred

class FakePaymentProcessor : PaymentProcessor {
    var nextResult: PaymentResult = PaymentResult.Approved("pi_test", "4242")
    var nextRefund: RefundResult = RefundResult.Refunded("re_test")
    val requests = mutableListOf<PaymentRequest>()
    val refundKeys = mutableListOf<String>()
    val refunds = mutableListOf<Refund>()
    val forwards = mutableListOf<Forward>()

    /* Set to hold authorize/refund mid-flight. */
    var gate: CompletableDeferred<Unit>? = null

    data class Refund(val method: PaymentMethod, val reference: String?, val amountCents: Long, val key: String)
    data class Forward(val saleId: String, val key: String, val amountCents: Long, val token: String)

    override suspend fun authorize(request: PaymentRequest): PaymentResult {
        requests += request
        gate?.await()
        return nextResult
    }

    override suspend fun forwardOffline(saleId: String, idempotencyKey: String, amountCents: Long, token: String): PaymentResult {
        forwards += Forward(saleId, idempotencyKey, amountCents, token)
        return nextResult
    }

    override suspend fun refund(method: PaymentMethod, paymentReference: String?, amountCents: Long, idempotencyKey: String): RefundResult {
        refundKeys += idempotencyKey
        refunds += Refund(method, paymentReference, amountCents, idempotencyKey)
        gate?.await()
        return nextRefund
    }
}
