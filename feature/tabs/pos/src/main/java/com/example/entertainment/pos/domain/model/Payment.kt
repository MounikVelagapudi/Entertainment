package com.example.entertainment.pos.domain.model

data class PaymentRequest(
    val saleId: String,
    /* Derived from the sale's key, so a retried charge can't double-authorize. */
    val idempotencyKey: String,
    val amountCents: Long,
    val method: PaymentMethod,
    val testCard: TestCard = TestCard.VISA_SUCCESS,
)

sealed interface PaymentResult {
    data class Approved(
        val reference: String?,
        val cardLast4: String?,
        val storedOffline: Boolean = false,
        /* Only for offline card auths -- what gets forwarded once online. Stripe
           Terminal keeps this encrypted inside the SDK; we never see a PAN. */
        val offlineToken: String? = null,
    ) : PaymentResult

    data class Declined(val reason: String) : PaymentResult
    data class Error(val message: String) : PaymentResult
}

sealed interface RefundResult {
    data class Refunded(val reference: String) : RefundResult
    data class Error(val message: String) : RefundResult
}
