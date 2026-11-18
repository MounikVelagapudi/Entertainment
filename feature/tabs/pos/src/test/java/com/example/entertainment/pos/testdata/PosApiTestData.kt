package com.example.entertainment.pos.testdata

import com.example.entertainment.pos.data.remote.MockBackend
import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import com.example.entertainment.pos.data.remote.PaymentDto
import com.example.entertainment.pos.data.remote.RefundDto
import com.example.entertainment.pos.data.remote.TransactionItemDto
import com.example.entertainment.pos.data.remote.TransactionRequest
import com.example.entertainment.pos.domain.model.TestCard
import kotlinx.serialization.json.Json

/* Request bodies and a backend for the mock POS API. */

/* No latency, no random 503s, one hot dog left, 99 of everything else. */
fun aMockBackend(
    latencyMs: Long = 0,
    failureRatePercent: Int = 0,
    initialStock: Map<String, Int> = mapOf(HOT_DOG_BARCODE to 1),
    defaultStock: Int = 99,
) = MockBackend(
    latencyMs = latencyMs,
    failureRatePercent = failureRatePercent,
    initialStock = initialStock,
    defaultStock = defaultStock,
)

fun aTransactionItemDto(barcode: String, quantity: Int = 1, name: String = "Item", unitPriceCents: Long = 100) =
    TransactionItemDto(barcode, name, unitPriceCents, quantity)

fun aSaleJson(barcode: String = TestProducts.POPCORN.barcode, managerOverride: Boolean = false): String =
    Json.encodeToString(
        TransactionRequest.serializer(),
        TransactionRequest(
            clientTransactionId = "tx-1",
            createdAtMillis = 0,
            currency = "USD",
            totalCents = 100,
            items = listOf(aTransactionItemDto(barcode)),
            managerOverride = managerOverride,
        ),
    )

fun aPaymentJson(cardNumber: String? = TestCard.VISA_SUCCESS.number, provider: String = "stripe"): String =
    Json.encodeToString(PaymentDto.serializer(), PaymentDto(provider, 100, testCardNumber = cardNumber))

fun aRefundJson(paymentReference: String = "pi_abcdef123456", amountCents: Long = 100): String =
    Json.encodeToString(RefundDto.serializer(), RefundDto(paymentReference, amountCents))
