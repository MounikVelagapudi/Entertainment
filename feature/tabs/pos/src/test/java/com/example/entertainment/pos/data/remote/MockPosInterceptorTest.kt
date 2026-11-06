package com.example.entertainment.pos.data.remote

import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import com.example.entertainment.pos.domain.model.TestCard
import com.example.entertainment.pos.fakes.MockPosHttpClient
import com.example.entertainment.pos.testdata.TestProducts.POPCORN
import com.example.entertainment.pos.testdata.aMockBackend
import com.example.entertainment.pos.testdata.aPaymentJson
import com.example.entertainment.pos.testdata.aRefundJson
import com.example.entertainment.pos.testdata.aSaleJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPosInterceptorTest {

    private val backend = aMockBackend()
    private val http = MockPosHttpClient(backend)

    private val hotDogSale = aSaleJson(HOT_DOG_BARCODE)

    private fun pay(key: String?, body: String = aPaymentJson()) = http.post(key, body, "v1/payments")
    private fun refund(key: String, body: String = aRefundJson()) = http.post(key, body, "v1/refunds")
    private fun errorOf(body: String) = Json.decodeFromString(PosErrorResponse.serializer(), body)

    @Test
    fun `accepts a new transaction`() {
        val (code, body) = http.post("abc-12345678")
        assertEquals(201, code)
        assertTrue(body.contains("\"reference\":\"TXN-12345678\""))
    }

    @Test
    fun `same idempotency key returns the original reference`() {
        http.post("abc-12345678")
        val (code, body) = http.post("abc-12345678")
        assertEquals(200, code)
        assertTrue(body.contains("TXN-12345678"))
        assertTrue(body.contains("DUPLICATE"))
    }

    @Test
    fun `second sale of the last hot dog is a 409 conflict`() {
        assertEquals(201, http.post("key-1", hotDogSale).first)

        val (code, body) = http.post("key-2", hotDogSale)
        assertEquals(409, code)
        val error = errorOf(body)
        assertEquals("INVENTORY_EXHAUSTED", error.error)
        assertEquals(HOT_DOG_BARCODE, error.conflictingBarcode)
    }

    @Test
    fun `manager override skips the stock check`() {
        http.post("key-1", hotDogSale)
        assertEquals(201, http.post("key-2", aSaleJson(HOT_DOG_BARCODE, managerOverride = true)).first)
        assertEquals(-1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `retrying an accepted sale does not conflict even once stock is gone`() {
        http.post("key-1", hotDogSale)
        assertEquals(200, http.post("key-1", hotDogSale).first)
    }

    @Test
    fun `success card is approved and replays the same payment id`() {
        val first = pay("pay-1")
        val second = pay("pay-1")
        assertEquals(200, first.first)
        assertTrue(first.second.contains("\"last4\":\"4242\""))
        assertEquals(first, second)
    }

    @Test
    fun `decline test cards return 402 with a decline code`() {
        val (code, body) = pay("pay-2", aPaymentJson(TestCard.INSUFFICIENT_FUNDS.number))
        assertEquals(402, code)
        assertEquals("insufficient_funds", errorOf(body).declineCode)
    }

    @Test
    fun `paypal gets a PAYID reference`() {
        val (code, body) = pay("pay-3", aPaymentJson(cardNumber = null, provider = "paypal"))
        assertEquals(200, code)
        assertTrue(body.contains("PAYID-"))
    }

    @Test
    fun `refund succeeds and is idempotent`() {
        val first = refund("ref-1")
        assertEquals(200, first.first)
        assertEquals(first, refund("ref-1"))
    }

    @Test
    fun `missing idempotency key is a 400`() {
        assertEquals(400, http.post(idempotencyKey = null).first)
    }

    @Test
    fun `always-fail mode returns 503 on transactions only`() {
        backend.setFailureRate(100)
        assertEquals(503, http.post("abc").first)
        assertEquals(200, pay("pay-4").first)
    }

    @Test
    fun `unknown path is a 404`() {
        assertEquals(404, http.post("abc", path = "v1/nope").first)
    }

    @Test
    fun `given a GET then it is a 404 even on a known path`() {
        val (code, body) = http.get("v1/transactions")
        assertEquals(404, code)
        assertTrue(body.contains("NOT_FOUND"))
    }

    @Test
    fun `given an unreadable transaction body then it is a 400 bad request`() {
        val (code, body) = http.post("k-bad", "{not json")
        assertEquals(400, code)
        assertTrue(body.contains("BAD_REQUEST"))
    }

    @Test
    fun `given unreadable payment and refund bodies then both are 400`() {
        assertEquals(400, pay("p-bad", "{}").first)
        assertEquals(400, refund("r-bad", "{}").first)
    }

    @Test
    fun `given a missing key on payments then it is a 400 with the key error`() {
        val (code, body) = pay(key = null)
        assertEquals(400, code)
        assertTrue(body.contains("MISSING_IDEMPOTENCY_KEY"))
    }

    @Test
    fun `given a 409 then the key is not remembered and a retry after restock succeeds`() {
        http.post("key-1", hotDogSale)
        assertEquals(409, http.post("key-2", hotDogSale).first)

        backend.adjustStock(HOT_DOG_BARCODE, 1)
        assertEquals(201, http.post("key-2", hotDogSale).first)
    }

    @Test
    fun `given a 409 then the error echoes the client transaction id`() {
        http.post("key-1", hotDogSale)
        val (_, body) = http.post("key-2", hotDogSale)
        assertEquals("tx-1", errorOf(body).clientTransactionId)
    }

    @Test
    fun `given an accepted sale then a replay is a duplicate even when the backend is failing`() {
        http.post("key-1")
        backend.setFailureRate(100)
        val (code, body) = http.post("key-1")
        assertEquals(200, code)
        assertTrue(body.contains("DUPLICATE"))
    }

    @Test
    fun `given a duplicate then stock is only decremented once`() {
        repeat(3) { http.post("key-1", hotDogSale) }
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given a 503 then the key is not burned and the retry is accepted`() {
        backend.setFailureRate(100)
        assertEquals(503, http.post("key-1").first)
        backend.setFailureRate(0)
        assertEquals(201, http.post("key-1").first)
    }

    @Test
    fun `given a failure rate then roughly that share of fresh sales get 503`() {
        backend.adjustStock(POPCORN.barcode, 1_000)
        backend.setFailureRate(20)
        val codes = (1..500).map { http.post("k-$it").first }
        val failures = codes.count { it == 503 }
        assertTrue("got $failures", failures in 60..140)
        assertTrue(codes.all { it == 201 || it == 503 })
    }

    @Test
    fun `given the generic decline card then 402 carries generic_decline`() {
        val (code, body) = pay("pay-g", aPaymentJson(TestCard.GENERIC_DECLINE.number))
        assertEquals(402, code)
        assertEquals("generic_decline", errorOf(body).declineCode)
    }

    @Test
    fun `given a declined key then the decline is replayed even with a good card`() {
        pay("pay-d", aPaymentJson(TestCard.GENERIC_DECLINE.number))
        assertEquals(402, pay("pay-d", aPaymentJson(TestCard.VISA_SUCCESS.number)).first)
    }

    @Test
    fun `given a stripe payment then the id has the pi_ prefix`() {
        val (_, body) = pay("pay-s", aPaymentJson(provider = "stripe_terminal"))
        assertTrue(Json.decodeFromString(PaymentResponse.serializer(), body).id.startsWith("pi_"))
    }

    @Test
    fun `given a refund then its id is derived from the payment reference`() {
        val (_, response) = refund("ref-2", aRefundJson("pi_abcdef123456"))
        assertEquals("re_ef123456", Json.decodeFromString(RefundResponse.serializer(), response).id)
    }

    @Test
    fun `given latency is configured then the call waits at least that long`() {
        backend.setLatency(150)
        val started = System.nanoTime()
        http.post("slow")
        assertTrue((System.nanoTime() - started) / 1_000_000 >= 150)
    }
}
