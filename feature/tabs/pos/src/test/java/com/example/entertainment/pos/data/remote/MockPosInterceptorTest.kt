package com.example.entertainment.pos.data.remote

import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import kotlin.random.Random
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPosInterceptorTest {

    private val backend = MockBackend(
        latencyMs = 0,
        failureRatePercent = 0,
        initialStock = mapOf(HOT_DOG_BARCODE to 1),
        defaultStock = 99,
    )
    private val client = OkHttpClient.Builder()
        .addInterceptor(MockPosInterceptor(backend, Random(42)))
        .build()

    private fun sale(barcode: String = "012345678905", override: Boolean = false) = Json.encodeToString(
        TransactionRequest.serializer(),
        TransactionRequest(
            clientTransactionId = "tx-1",
            createdAtMillis = 0,
            currency = "USD",
            totalCents = 100,
            items = listOf(TransactionItemDto(barcode, "Item", 100, 1)),
            managerOverride = override,
        ),
    )

    private fun payment(card: String?, provider: String = "stripe") =
        Json.encodeToString(PaymentDto.serializer(), PaymentDto(provider, 100, testCardNumber = card))

    private fun post(key: String?, body: String = sale(), path: String = "v1/transactions") = Request.Builder()
        .url(PosApi.BASE_URL + path)
        .post(body.toRequestBody("application/json".toMediaType()))
        .apply { key?.let { header("Idempotency-Key", it) } }
        .build()

    private fun call(request: Request) = client.newCall(request).execute().use { it.code to it.body.string() }

    @Test
    fun `accepts a new transaction`() {
        val (code, body) = call(post("abc-12345678"))
        assertEquals(201, code)
        assertTrue(body.contains("\"reference\":\"TXN-12345678\""))
    }

    @Test
    fun `same idempotency key returns the original reference`() {
        call(post("abc-12345678"))
        val (code, body) = call(post("abc-12345678"))
        assertEquals(200, code)
        assertTrue(body.contains("TXN-12345678"))
        assertTrue(body.contains("DUPLICATE"))
    }

    @Test
    fun `second sale of the last hot dog is a 409 conflict`() {
        assertEquals(201, call(post("key-1", sale(HOT_DOG_BARCODE))).first)

        val (code, body) = call(post("key-2", sale(HOT_DOG_BARCODE)))
        assertEquals(409, code)
        val error = Json.decodeFromString(PosErrorResponse.serializer(), body)
        assertEquals("INVENTORY_EXHAUSTED", error.error)
        assertEquals(HOT_DOG_BARCODE, error.conflictingBarcode)
    }

    @Test
    fun `manager override skips the stock check`() {
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        assertEquals(201, call(post("key-2", sale(HOT_DOG_BARCODE, override = true))).first)
        assertEquals(-1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `retrying an accepted sale does not conflict even once stock is gone`() {
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        assertEquals(200, call(post("key-1", sale(HOT_DOG_BARCODE))).first)
    }

    @Test
    fun `success card is approved and replays the same payment id`() {
        val first = call(post("pay-1", payment("4242424242424242"), "v1/payments"))
        val second = call(post("pay-1", payment("4242424242424242"), "v1/payments"))
        assertEquals(200, first.first)
        assertTrue(first.second.contains("\"last4\":\"4242\""))
        assertEquals(first, second)
    }

    @Test
    fun `decline test cards return 402 with a decline code`() {
        val (code, body) = call(post("pay-2", payment("4000000000009995"), "v1/payments"))
        assertEquals(402, code)
        assertEquals("insufficient_funds", Json.decodeFromString(PosErrorResponse.serializer(), body).declineCode)
    }

    @Test
    fun `paypal gets a PAYID reference`() {
        val (code, body) = call(post("pay-3", payment(null, provider = "paypal"), "v1/payments"))
        assertEquals(200, code)
        assertTrue(body.contains("PAYID-"))
    }

    @Test
    fun `refund succeeds and is idempotent`() {
        val body = Json.encodeToString(RefundDto.serializer(), RefundDto("pi_abcdef123456", 100))
        val first = call(post("ref-1", body, "v1/refunds"))
        assertEquals(200, first.first)
        assertEquals(first, call(post("ref-1", body, "v1/refunds")))
    }

    @Test
    fun `missing idempotency key is a 400`() {
        assertEquals(400, call(post(key = null)).first)
    }

    @Test
    fun `always-fail mode returns 503 on transactions only`() {
        backend.setFailureRate(100)
        assertEquals(503, call(post("abc")).first)
        assertEquals(200, call(post("pay-4", payment("4242424242424242"), "v1/payments")).first)
    }

    @Test
    fun `unknown path is a 404`() {
        assertEquals(404, call(post("abc", path = "v1/nope")).first)
    }

    @Test
    fun `given a GET then it is a 404 even on a known path`() {
        val request = Request.Builder().url(PosApi.BASE_URL + "v1/transactions").get().build()
        val (code, body) = call(request)
        assertEquals(404, code)
        assertTrue(body.contains("NOT_FOUND"))
    }

    @Test
    fun `given an unreadable transaction body then it is a 400 bad request`() {
        val (code, body) = call(post("k-bad", "{not json"))
        assertEquals(400, code)
        assertTrue(body.contains("BAD_REQUEST"))
    }

    @Test
    fun `given unreadable payment and refund bodies then both are 400`() {
        assertEquals(400, call(post("p-bad", "{}", "v1/payments")).first)
        assertEquals(400, call(post("r-bad", "{}", "v1/refunds")).first)
    }

    @Test
    fun `given a missing key on payments then it is a 400 with the key error`() {
        val (code, body) = call(post(null, payment("4242424242424242"), "v1/payments"))
        assertEquals(400, code)
        assertTrue(body.contains("MISSING_IDEMPOTENCY_KEY"))
    }

    @Test
    fun `given a 409 then the key is not remembered and a retry after restock succeeds`() {
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        assertEquals(409, call(post("key-2", sale(HOT_DOG_BARCODE))).first)

        backend.adjustStock(HOT_DOG_BARCODE, 1)
        assertEquals(201, call(post("key-2", sale(HOT_DOG_BARCODE))).first)
    }

    @Test
    fun `given a 409 then the error echoes the client transaction id`() {
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        val (_, body) = call(post("key-2", sale(HOT_DOG_BARCODE)))
        assertEquals("tx-1", Json.decodeFromString(PosErrorResponse.serializer(), body).clientTransactionId)
    }

    @Test
    fun `given an accepted sale then a replay is a duplicate even when the backend is failing`() {
        call(post("key-1"))
        backend.setFailureRate(100)
        val (code, body) = call(post("key-1"))
        assertEquals(200, code)
        assertTrue(body.contains("DUPLICATE"))
    }

    @Test
    fun `given a duplicate then stock is only decremented once`() {
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        call(post("key-1", sale(HOT_DOG_BARCODE)))
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given a 503 then the key is not burned and the retry is accepted`() {
        backend.setFailureRate(100)
        assertEquals(503, call(post("key-1")).first)
        backend.setFailureRate(0)
        assertEquals(201, call(post("key-1")).first)
    }

    @Test
    fun `given a failure rate then roughly that share of fresh sales get 503`() {
        backend.adjustStock("012345678905", 1_000)
        backend.setFailureRate(20)
        val codes = (1..500).map { call(post("k-$it")).first }
        val failures = codes.count { it == 503 }
        assertTrue("got $failures", failures in 60..140)
        assertTrue(codes.all { it == 201 || it == 503 })
    }

    @Test
    fun `given the generic decline card then 402 carries generic_decline`() {
        val (code, body) = call(post("pay-g", payment("4000000000000002"), "v1/payments"))
        assertEquals(402, code)
        assertEquals("generic_decline", Json.decodeFromString(PosErrorResponse.serializer(), body).declineCode)
    }

    @Test
    fun `given a declined key then the decline is replayed even with a good card`() {
        call(post("pay-d", payment("4000000000000002"), "v1/payments"))
        assertEquals(402, call(post("pay-d", payment("4242424242424242"), "v1/payments")).first)
    }

    @Test
    fun `given a stripe payment then the id has the pi_ prefix`() {
        val (_, body) = call(post("pay-s", payment("4242424242424242", provider = "stripe_terminal"), "v1/payments"))
        assertTrue(Json.decodeFromString(PaymentResponse.serializer(), body).id.startsWith("pi_"))
    }

    @Test
    fun `given a refund then its id is derived from the payment reference`() {
        val body = Json.encodeToString(RefundDto.serializer(), RefundDto("pi_abcdef123456", 100))
        val (_, response) = call(post("ref-2", body, "v1/refunds"))
        assertEquals("re_ef123456", Json.decodeFromString(RefundResponse.serializer(), response).id)
    }

    @Test
    fun `given latency is configured then the call waits at least that long`() {
        backend.setLatency(150)
        val started = System.nanoTime()
        call(post("slow"))
        assertTrue((System.nanoTime() - started) / 1_000_000 >= 150)
    }
}
