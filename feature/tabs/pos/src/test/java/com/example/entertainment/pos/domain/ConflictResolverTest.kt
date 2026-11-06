package com.example.entertainment.pos.domain

import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.givenSale
import com.example.entertainment.pos.fakes.transaction
import com.example.entertainment.pos.testdata.TestProducts.HOT_DOG
import com.example.entertainment.pos.testdata.aCartLine
import com.example.entertainment.pos.testdata.anApprovedPayment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {

    private val given = ConflictResolverBuilder()
    private val repository = given.repository
    private val payments = given.payments

    /* One hot dog on the card terminal that the backend rejected for stock. */
    private suspend fun conflictedSale() = repository.givenSale(
        status = SyncStatus.CONFLICT,
        lines = listOf(aCartLine(HOT_DOG)),
        statusReason = "INVENTORY_EXHAUSTED",
    )

    @Test
    fun `void refunds with a refund-scoped idempotency key, then voids`() = runTest {
        val id = conflictedSale()

        val result = given.build().voidAndRefund(repository.transaction(id))

        assertTrue(result is RefundResult.Refunded)
        assertEquals(listOf("key-$id:refund"), payments.refundKeys)
        assertEquals(SyncStatus.VOIDED, repository.transaction(id).status)
        assertEquals(PaymentStatus.REFUNDED, repository.transaction(id).paymentStatus)
    }

    @Test
    fun `failed refund leaves the sale in conflict`() = runTest {
        val id = conflictedSale()
        val resolver = given.withRefundResult(RefundResult.Error("Network error, try again")).build()

        resolver.voidAndRefund(repository.transaction(id))

        assertEquals(SyncStatus.CONFLICT, repository.transaction(id).status)
        assertTrue(repository.events.contains(id to "REFUND_FAILED"))
    }

    @Test
    fun `manager override requeues and schedules a sync`() = runTest {
        val id = conflictedSale()
        val before = repository.syncRequests

        given.build().managerOverride(repository.transaction(id))

        assertEquals(SyncStatus.PENDING, repository.transaction(id).status)
        assertTrue(repository.transaction(id).managerOverride)
        assertEquals(before + 1, repository.syncRequests)
    }

    @Test
    fun `ship to home closes the conflict`() = runTest {
        val id = conflictedSale()

        given.build().shipToHome(repository.transaction(id))

        assertEquals(SyncStatus.SHIP_TO_HOME, repository.transaction(id).status)
    }

    @Test
    fun `given a conflict when refunding then method reference and full total go to the processor`() = runTest {
        val id = conflictedSale()

        given.build().voidAndRefund(repository.transaction(id))

        assertEquals(
            FakePaymentProcessor.Refund(PaymentMethod.CARD_TERMINAL, "pi_1", 599, "key-$id:refund"),
            payments.refunds.single(),
        )
    }

    @Test
    fun `given a successful refund then its result is returned to the caller`() = runTest {
        val id = conflictedSale()
        val resolver = given.withRefundResult(RefundResult.Refunded("re_abc")).build()

        assertEquals(RefundResult.Refunded("re_abc"), resolver.voidAndRefund(repository.transaction(id)))
    }

    @Test
    fun `given a failed refund then the error is returned and no void is recorded`() = runTest {
        val id = conflictedSale()
        val resolver = given.withRefundResult(RefundResult.Error("Refund failed (500)")).build()

        val result = resolver.voidAndRefund(repository.transaction(id))

        assertEquals(RefundResult.Error("Refund failed (500)"), result)
        assertEquals(PaymentStatus.APPROVED, repository.transaction(id).paymentStatus)
    }

    @Test
    fun `given a sale that is not in conflict when voiding then it throws and refunds nothing`() = runTest {
        val id = conflictedSale()
        repository.update(id) { it.copy(status = SyncStatus.SYNCED) }

        val error = runCatching { given.build().voidAndRefund(repository.transaction(id)) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(payments.refunds.isEmpty())
        assertEquals(SyncStatus.SYNCED, repository.transaction(id).status)
    }

    @Test
    fun `given every non-conflict status when voiding then it is rejected`() = runTest {
        val id = conflictedSale()
        val resolver = given.build()

        SyncStatus.entries.filter { it != SyncStatus.CONFLICT }.forEach { status ->
            repository.update(id) { it.copy(status = status) }
            val error = runCatching { resolver.voidAndRefund(repository.transaction(id)) }.exceptionOrNull()
            assertTrue(status.name, error is IllegalStateException)
        }
        assertTrue(payments.refunds.isEmpty())
    }

    @Test
    fun `given the sale has no stored key when voiding then it fails before any refund`() = runTest {
        val id = conflictedSale()
        repository.unknownIds += id

        val error = runCatching { given.build().voidAndRefund(repository.transaction(id)) }.exceptionOrNull()

        assertEquals("Unknown sale", error?.message)
        assertTrue(payments.refunds.isEmpty())
        assertEquals(SyncStatus.CONFLICT, repository.transaction(id).status)
    }

    @Test
    fun `given a cash conflict when voiding then the cash method and null reference are passed through`() = runTest {
        val id = repository.givenSale(
            status = SyncStatus.CONFLICT,
            lines = listOf(aCartLine(HOT_DOG, quantity = 3)),
            method = PaymentMethod.CASH,
            payment = anApprovedPayment(reference = null, cardLast4 = null),
        )

        given.build().voidAndRefund(repository.transaction(id))

        val refund = payments.refunds.single()
        assertEquals(PaymentMethod.CASH, refund.method)
        assertNull(refund.reference)
        assertEquals(1797L, refund.amountCents)
        assertEquals(SyncStatus.VOIDED, repository.transaction(id).status)
    }

    @Test
    fun `given an already voided sale when ship to home is tried then it stays voided`() = runTest {
        val id = conflictedSale()
        val resolver = given.build()
        resolver.voidAndRefund(repository.transaction(id))

        resolver.shipToHome(repository.transaction(id))

        assertEquals(SyncStatus.VOIDED, repository.transaction(id).status)
    }
}
