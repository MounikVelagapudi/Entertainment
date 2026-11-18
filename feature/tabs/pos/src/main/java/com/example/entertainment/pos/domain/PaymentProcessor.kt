package com.example.entertainment.pos.domain

import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentRequest
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.RefundResult

/**
 * The only payment seam checkout knows about. Real Stripe Terminal / PaymentSheet /
 * PayPal SDKs slot in behind this -- see the module README.
 */
interface PaymentProcessor {
    suspend fun authorize(request: PaymentRequest): PaymentResult

    /** Sends a card auth that was taken offline. Can still decline. */
    suspend fun forwardOffline(saleId: String, idempotencyKey: String, amountCents: Long, token: String): PaymentResult

    suspend fun refund(
        method: PaymentMethod,
        paymentReference: String?,
        amountCents: Long,
        idempotencyKey: String,
    ): RefundResult
}
