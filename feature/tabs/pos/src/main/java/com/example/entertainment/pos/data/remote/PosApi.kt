package com.example.entertainment.pos.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** Contract served by [MockPosInterceptor] -- see the module README. */
interface PosApi {

    @POST("v1/transactions")
    suspend fun postTransaction(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: TransactionRequest,
    ): TransactionResponse

    /* Stands in for "backend creates + confirms a PaymentIntent / captures a PayPal
       order". Card data never goes through our API in the real thing. */
    @POST("v1/payments")
    suspend fun createPayment(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: PaymentDto,
    ): PaymentResponse

    @POST("v1/refunds")
    suspend fun createRefund(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: RefundDto,
    ): RefundResponse

    companion object {
        const val BASE_URL = "https://pos.mock.local/"
    }
}

@Serializable
data class TransactionRequest(
    val clientTransactionId: String,
    val createdAtMillis: Long,
    val currency: String,
    val totalCents: Long,
    val items: List<TransactionItemDto>,
    val paymentMethod: String = "CASH",
    val paymentReference: String? = null,
    val managerOverride: Boolean = false,
)

@Serializable
data class TransactionItemDto(
    val barcode: String,
    val name: String,
    val unitPriceCents: Long,
    val quantity: Int,
)

@Serializable
data class TransactionResponse(
    val reference: String,
    val status: String,
    val receivedAtMillis: Long,
)

/** Error body for 4xx. 409 + INVENTORY_EXHAUSTED is the offline-oversell case. */
@Serializable
data class PosErrorResponse(
    val error: String,
    val conflictingBarcode: String? = null,
    val clientTransactionId: String? = null,
    val declineCode: String? = null,
)

@Serializable
data class PaymentDto(
    /* stripe_terminal | stripe | paypal */
    val provider: String,
    val amountCents: Long,
    val currency: String = "USD",
    /* Test card number for the simulator; a real integration never sends a PAN. */
    val testCardNumber: String? = null,
)

@Serializable
data class PaymentResponse(
    val id: String,
    val status: String,
    val last4: String? = null,
)

@Serializable
data class RefundDto(
    val paymentReference: String,
    val amountCents: Long,
)

@Serializable
data class RefundResponse(
    val id: String,
    val status: String,
)
