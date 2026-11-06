package com.example.entertainment.pos.data.payment

import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.domain.model.TestCard
import com.example.entertainment.pos.testdata.aPaymentRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedPaymentProcessorTest {

    private val given = SimulatedPaymentProcessorBuilder()
    private val sentRequests get() = given.recorder.requests

    @Test
    fun `given cash then it approves locally without touching the api`() = runTest {
        val result = given.build().authorize(aPaymentRequest(PaymentMethod.CASH))

        assertEquals(PaymentResult.Approved(reference = "CASH-sale-123", cardLast4 = null), result)
        assertTrue(sentRequests.isEmpty())
    }

    @Test
    fun `given cash while offline then it still approves`() = runTest {
        val processor = given.withNetworkDown().build()

        assertTrue(processor.authorize(aPaymentRequest(PaymentMethod.CASH)) is PaymentResult.Approved)
    }

    @Test
    fun `given card terminal online then a stripe_terminal payment is created with the sale key`() = runTest {
        val result = given.build().authorize(aPaymentRequest(PaymentMethod.CARD_TERMINAL)) as PaymentResult.Approved

        assertTrue(result.reference!!.startsWith("pi_"))
        assertEquals("4242", result.cardLast4)
        assertFalse(result.storedOffline)
        val sent = sentRequests.single()
        assertEquals("/v1/payments", sent.path)
        assertEquals("k:pay", sent.idempotencyKey)
        assertTrue(sent.body.contains("\"provider\":\"stripe_terminal\""))
        assertTrue(sent.body.contains("\"amountCents\":1798"))
    }

    @Test
    fun `given card terminal offline then it is stored offline with the card as token`() = runTest {
        val processor = given.withNetworkDown().build()

        val result = processor.authorize(aPaymentRequest(PaymentMethod.CARD_TERMINAL)) as PaymentResult.Approved

        assertNull(result.reference)
        assertTrue(result.storedOffline)
        assertEquals("4242", result.cardLast4)
        assertEquals(TestCard.VISA_SUCCESS.number, result.offlineToken)
        assertTrue(sentRequests.isEmpty())
    }

    @Test
    fun `given a decline card offline then it is still approved locally`() = runTest {
        val processor = given.withNetworkDown().build()

        val result = processor.authorize(aPaymentRequest(PaymentMethod.CARD_TERMINAL, TestCard.GENERIC_DECLINE))

        assertTrue((result as PaymentResult.Approved).storedOffline)
    }

    @Test
    fun `given online-only methods while offline then they error without a call`() = runTest {
        val processor = given.withNetworkDown().build()

        assertEquals(
            PaymentResult.Error("Stripe online needs a connection"),
            processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE)),
        )
        assertEquals(PaymentResult.Error("PayPal needs a connection"), processor.authorize(aPaymentRequest(PaymentMethod.PAYPAL)))
        assertTrue(sentRequests.isEmpty())
    }

    @Test
    fun `given decline cards online then the decline code comes back`() = runTest {
        val processor = given.build()

        assertEquals(
            PaymentResult.Declined("generic_decline"),
            processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE, TestCard.GENERIC_DECLINE, idempotencyKey = "a")),
        )
        assertEquals(
            PaymentResult.Declined("insufficient_funds"),
            processor.authorize(aPaymentRequest(PaymentMethod.CARD_TERMINAL, TestCard.INSUFFICIENT_FUNDS, idempotencyKey = "b")),
        )
    }

    @Test
    fun `given paypal then no card number is sent and a PAYID reference comes back`() = runTest {
        val result = given.build().authorize(aPaymentRequest(PaymentMethod.PAYPAL)) as PaymentResult.Approved

        assertTrue(result.reference!!.startsWith("PAYID-"))
        assertNull(result.cardLast4)
        assertFalse(sentRequests.single().body.contains("testCardNumber"))
    }

    @Test
    fun `given a retried charge with the same key then the same payment id comes back`() = runTest {
        val processor = given.build()

        val first = processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE))
        val second = processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE))

        assertEquals(first, second)
    }

    @Test
    fun `given a network failure then it is a retryable error`() = runTest {
        val processor = given.withIoFailure().build()

        assertEquals(PaymentResult.Error("Network error, try again"), processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE)))
    }

    @Test
    fun `given a non-402 http error then it is a generic payment failure`() = runTest {
        val processor = given.withHttpResponse(500, """{"error":"BOOM"}""").build()

        assertEquals(PaymentResult.Error("Payment failed (500)"), processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE)))
    }

    @Test
    fun `given a 402 with an unreadable body then it falls back to card_declined`() = runTest {
        val processor = given.withHttpResponse(402, "nope").build()

        assertEquals(PaymentResult.Declined("card_declined"), processor.authorize(aPaymentRequest(PaymentMethod.STRIPE_ONLINE)))
    }

    @Test
    fun `given an offline auth when forwarded then it charges stripe_terminal with the token`() = runTest {
        val result = given.build().forwardOffline("sale-1", "k:pay", 599, TestCard.VISA_SUCCESS.number) as PaymentResult.Approved

        assertTrue(result.reference!!.startsWith("pi_"))
        val sent = sentRequests.single()
        assertEquals("k:pay", sent.idempotencyKey)
        assertTrue(sent.body.contains("\"testCardNumber\":\"4242424242424242\""))
        assertTrue(sent.body.contains("\"amountCents\":599"))
    }

    @Test
    fun `given a forwarded decline card then the forward is declined`() = runTest {
        assertEquals(
            PaymentResult.Declined("insufficient_funds"),
            given.build().forwardOffline("sale-1", "k:pay", 599, TestCard.INSUFFICIENT_FUNDS.number),
        )
    }

    @Test
    fun `given a cash sale or no reference when refunding then it refunds locally`() = runTest {
        val processor = given.build()

        val cash = processor.refund(PaymentMethod.CASH, "CASH-123", 100, "k:refund")
        val noRef = processor.refund(PaymentMethod.CARD_TERMINAL, null, 100, "k:refund")

        assertTrue((cash as RefundResult.Refunded).reference.startsWith("CASH-REFUND-"))
        assertTrue((noRef as RefundResult.Refunded).reference.startsWith("CASH-REFUND-"))
        assertTrue(sentRequests.isEmpty())
    }

    @Test
    fun `given a card payment when refunding then the api refunds with the refund key`() = runTest {
        val result = given.build().refund(PaymentMethod.STRIPE_ONLINE, "pi_abcdef123456", 1798, "k:refund")

        assertEquals(RefundResult.Refunded("re_ef123456"), result)
        val sent = sentRequests.single()
        assertEquals("/v1/refunds", sent.path)
        assertEquals("k:refund", sent.idempotencyKey)
    }

    @Test
    fun `given refund network or http failures then they map to errors`() = runTest {
        val processor = given.withIoFailure().build()
        assertEquals(RefundResult.Error("Network error, try again"), processor.refund(PaymentMethod.PAYPAL, "PAYID-1", 100, "k"))

        given.recorder.throwIo = false
        given.withHttpResponse(502, "{}")
        assertEquals(RefundResult.Error("Refund failed (502)"), processor.refund(PaymentMethod.PAYPAL, "PAYID-1", 100, "k"))
    }
}
