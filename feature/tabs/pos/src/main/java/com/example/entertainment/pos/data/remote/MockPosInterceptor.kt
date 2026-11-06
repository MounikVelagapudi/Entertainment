package com.example.entertainment.pos.data.remote

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * Fake backend. Short-circuits the chain, so nothing leaves the device -- but
 * WorkManager's CONNECTED constraint and NetworkMonitor still use the real network
 * state, so airplane mode is a fair offline test. Full contract in the module README.
 *
 * Every POST needs an Idempotency-Key; a repeated key gets the first response back
 * (DUPLICATE for transactions), which is how Stripe/PayPal behave too.
 */
@Singleton
class MockPosInterceptor(
    private val backend: MockBackend,
    private val random: Random,
) : Interceptor {

    @Inject
    constructor(backend: MockBackend) : this(backend, Random.Default)

    private val json = Json { ignoreUnknownKeys = true }
    private val transactionRefs = ConcurrentHashMap<String, String>()
    private val paymentResponses = ConcurrentHashMap<String, Pair<Int, String>>()
    private val refundResponses = ConcurrentHashMap<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "POST") return respond(request, 404, error("NOT_FOUND"))

        Thread.sleep(backend.latencyMs.value)

        val key = request.header("Idempotency-Key")
            ?: return respond(request, 400, error("MISSING_IDEMPOTENCY_KEY"))

        return when (request.url.encodedPath) {
            "/v1/transactions" -> postTransaction(request, key)
            "/v1/payments" -> postPayment(request, key)
            "/v1/refunds" -> postRefund(request, key)
            else -> respond(request, 404, error("NOT_FOUND"))
        }
    }

    private fun postTransaction(request: Request, key: String): Response {
        /* Replay check comes before stock -- a retried sale we already took must not 409. */
        transactionRefs[key]?.let { return respond(request, 200, accepted(it, "DUPLICATE")) }

        /* Random 503s only here, so checkout itself stays predictable. */
        if (random.nextInt(100) < backend.failureRatePercent.value) {
            return respond(request, 503, error("SERVICE_UNAVAILABLE"))
        }

        val sale = request.decode<TransactionRequest>()
            ?: return respond(request, 400, error("BAD_REQUEST"))

        backend.tryReserve(sale.items, sale.managerOverride)?.let { short ->
            val body = PosErrorResponse("INVENTORY_EXHAUSTED", short, sale.clientTransactionId)
            return respond(request, 409, json.encodeToString(PosErrorResponse.serializer(), body))
        }

        val reference = "TXN-" + key.takeLast(8).uppercase()
        transactionRefs[key] = reference
        return respond(request, 201, accepted(reference, "ACCEPTED"))
    }

    private fun postPayment(request: Request, key: String): Response {
        paymentResponses[key]?.let { (code, body) -> return respond(request, code, body) }

        val payment = request.decode<PaymentDto>()
            ?: return respond(request, 400, error("BAD_REQUEST"))

        val declineCode = when (payment.testCardNumber) {
            "4000000000000002" -> "generic_decline"
            "4000000000009995" -> "insufficient_funds"
            else -> null
        }
        val (code, body) = if (declineCode != null) {
            402 to json.encodeToString(PosErrorResponse.serializer(), PosErrorResponse("card_declined", declineCode = declineCode))
        } else {
            val prefix = if (payment.provider == "paypal") "PAYID-" else "pi_"
            val response = PaymentResponse(
                id = prefix + UUID.randomUUID().toString().take(12),
                status = "succeeded",
                last4 = payment.testCardNumber?.takeLast(4),
            )
            200 to json.encodeToString(PaymentResponse.serializer(), response)
        }
        paymentResponses[key] = code to body
        return respond(request, code, body)
    }

    private fun postRefund(request: Request, key: String): Response {
        val body = refundResponses.getOrPut(key) {
            val refund = request.decode<RefundDto>()
                ?: return respond(request, 400, error("BAD_REQUEST"))
            json.encodeToString(
                RefundResponse.serializer(),
                RefundResponse("re_" + refund.paymentReference.takeLast(8), "succeeded"),
            )
        }
        return respond(request, 200, body)
    }

    private inline fun <reified T> Request.decode(): T? = runCatching {
        val buffer = Buffer().also { body?.writeTo(it) }
        json.decodeFromString<T>(buffer.readUtf8())
    }.getOrNull()

    private fun accepted(reference: String, status: String) =
        """{"reference":"$reference","status":"$status","receivedAtMillis":${System.currentTimeMillis()}}"""

    private fun error(code: String) = """{"error":"$code"}"""

    private fun respond(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 300) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
