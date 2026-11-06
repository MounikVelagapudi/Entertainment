package com.example.entertainment.pos.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.fakes.givenPaidSale
import com.example.entertainment.pos.testdata.TestProducts.HOT_DOG
import com.example.entertainment.pos.testdata.TestProducts.POPCORN
import com.example.entertainment.pos.testdata.aCartLine
import com.example.entertainment.pos.testdata.aTransactionEntity
import com.example.entertainment.pos.testdata.anApprovedPayment
import com.example.entertainment.pos.testdata.anOfflineCardPayment
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstPosRepositoryTest {

    private val given = OfflineFirstPosRepositoryBuilder()
    private val dao = given.dao
    private val repository = given.build()

    private val cart = listOf(aCartLine(POPCORN, 2), aCartLine(HOT_DOG, 1))

    @After
    fun tearDown() = given.close()

    private suspend fun tx(id: String) = repository.observeTransaction(id).first()
    private suspend fun events(id: String) = repository.observeEvents(id).first().map { it.type }

    private suspend fun paidSale(payment: PaymentResult.Approved = anApprovedPayment()) =
        repository.givenPaidSale(cart, PaymentMethod.CARD_TERMINAL, payment)

    /* What the sync worker does when the backend rejects a sale. */
    private suspend fun reject(id: String, status: SyncStatus, reason: String = "INVENTORY_EXHAUSTED", barcode: String? = HOT_DOG.barcode) =
        dao.markRejected(id, status, reason, barcode)

    @Test
    fun `given an empty cart when creating a draft then it is rejected`() = runTest {
        val error = runCatching { repository.createDraft(emptyList(), PaymentMethod.CASH) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `given a cart when a draft is created then header lines and created event are saved`() = runTest {
        val draft = repository.createDraft(cart, PaymentMethod.CARD_TERMINAL)

        val saved = tx(draft.id)!!
        assertEquals(SyncStatus.AWAITING_PAYMENT, saved.status)
        assertEquals(899L * 2 + 599, draft.totalCents)
        assertEquals(draft.totalCents, saved.totalCents)
        assertEquals(cart, saved.lines)
        assertEquals(PaymentMethod.CARD_TERMINAL, saved.paymentMethod)
        assertEquals(PaymentStatus.NONE, saved.paymentStatus)
        assertEquals(listOf("CREATED"), events(draft.id))
        assertEquals("Card (terminal)", repository.observeEvents(draft.id).first().single().detail)
    }

    @Test
    fun `given a draft then no sync is scheduled before payment`() = runTest {
        repository.createDraft(cart, PaymentMethod.CASH)
        verify(exactly = 0) { given.scheduler.requestSync() }
    }

    @Test
    fun `given a draft then the idempotency key is a stored v4 uuid separate from the id`() = runTest {
        val draft = repository.createDraft(cart, PaymentMethod.CASH)

        assertEquals(draft.idempotencyKey, repository.idempotencyKeyOf(draft.id))
        assertNotEquals(draft.id, draft.idempotencyKey)
        assertEquals(4, UUID.fromString(draft.idempotencyKey).version())
    }

    @Test
    fun `given two sales then each gets its own id and key`() = runTest {
        val a = repository.createDraft(cart, PaymentMethod.CASH)
        val b = repository.createDraft(cart, PaymentMethod.CASH)
        assertNotEquals(a.id, b.id)
        assertNotEquals(a.idempotencyKey, b.idempotencyKey)
    }

    @Test
    fun `given an unknown id then the idempotency key is null`() = runTest {
        assertNull(repository.idempotencyKeyOf("nope"))
    }

    @Test
    fun `given an approved payment then the sale goes pending and a sync is requested`() = runTest {
        val id = paidSale()

        val saved = tx(id)!!
        assertEquals(SyncStatus.PENDING, saved.status)
        assertEquals(PaymentStatus.APPROVED, saved.paymentStatus)
        assertEquals("pi_1", saved.paymentReference)
        assertEquals("4242", saved.cardLast4)
        assertEquals(listOf("CREATED", "PAYMENT_APPROVED"), events(id))
        assertEquals("pi_1", repository.observeEvents(id).first().last().detail)
        verify(exactly = 1) { given.scheduler.requestSync() }
    }

    @Test
    fun `given an offline card auth then it is stored offline with its token kept for the worker`() = runTest {
        val id = paidSale(anOfflineCardPayment())

        assertEquals(PaymentStatus.STORED_OFFLINE, tx(id)!!.paymentStatus)
        assertEquals("4242424242424242", dao.getByStatus(SyncStatus.PENDING).single().header.offlinePaymentToken)
        val event = repository.observeEvents(id).first().last()
        assertEquals("PAYMENT_STORED_OFFLINE", event.type)
        assertEquals("···4242", event.detail)
    }

    @Test
    fun `given a sale already synced when payment completes again then the status is not reset`() = runTest {
        val id = paidSale()
        dao.markSynced(id, "TXN-1")

        repository.completePayment(id, anApprovedPayment(reference = "pi_2", cardLast4 = "0002"))

        val saved = tx(id)!!
        assertEquals(SyncStatus.SYNCED, saved.status)
        assertEquals("pi_1", saved.paymentReference)
    }

    @Test
    fun `given a declined draft when discarded then the header lines and events are gone`() = runTest {
        val draft = repository.createDraft(cart, PaymentMethod.STRIPE_ONLINE)

        repository.discardDraft(draft.id, "Declined: generic_decline")

        assertNull(tx(draft.id))
        assertTrue(events(draft.id).isEmpty())
        assertTrue(dao.getByStatus(SyncStatus.AWAITING_PAYMENT).isEmpty())
    }

    @Test
    fun `given a paid sale when discard is called then the sale is kept`() = runTest {
        val id = paidSale()
        repository.discardDraft(id, "late cancel")
        assertEquals(SyncStatus.PENDING, tx(id)!!.status)
    }

    @Test
    fun `given sales in every state then pending and attention counts are right`() = runTest {
        repository.createDraft(cart, PaymentMethod.CASH)
        paidSale()
        val conflict = paidSale().also { reject(it, SyncStatus.CONFLICT) }
        paidSale().also { reject(it, SyncStatus.FAILED, "HTTP_422", null) }
        paidSale().also { dao.markSynced(it, "TXN") }

        assertEquals(1, repository.observePendingCount().first())
        assertEquals(3, repository.observeNeedsAttentionCount().first())

        repository.markShipToHome(conflict)
        assertEquals(2, repository.observeNeedsAttentionCount().first())
    }

    @Test
    fun `given sales at different times then observe all is newest first`() = runTest {
        listOf("old" to 1L, "new" to 3L, "mid" to 2L).forEach { (id, at) ->
            dao.insertSale(aTransactionEntity(id, createdAtMillis = at), emptyList())
        }
        assertEquals(listOf("new", "mid", "old"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `given a conflict when refunded and voided then it is voided refunded and logged`() = runTest {
        val id = paidSale().also { reject(it, SyncStatus.CONFLICT) }

        repository.markRefundedAndVoided(id, "re_1")

        val saved = tx(id)!!
        assertEquals(SyncStatus.VOIDED, saved.status)
        assertEquals(PaymentStatus.REFUNDED, saved.paymentStatus)
        assertEquals("VOIDED_AND_REFUNDED", events(id).last())
    }

    @Test
    fun `given a sale not in conflict then void override and ship to home all no-op without events`() = runTest {
        val id = paidSale()
        dao.markSynced(id, "TXN-1")
        val before = events(id)

        repository.markRefundedAndVoided(id, "re_1")
        repository.requeueWithManagerOverride(id)
        repository.markShipToHome(id)

        assertEquals(SyncStatus.SYNCED, tx(id)!!.status)
        assertEquals(PaymentStatus.APPROVED, tx(id)!!.paymentStatus)
        assertEquals(before, events(id))
    }

    @Test
    fun `given a conflict when manager override is applied then it is pending again with the reason cleared`() = runTest {
        val id = paidSale().also { reject(it, SyncStatus.CONFLICT) }

        repository.requeueWithManagerOverride(id)

        val saved = tx(id)!!
        assertEquals(SyncStatus.PENDING, saved.status)
        assertTrue(saved.managerOverride)
        assertNull(saved.statusReason)
        assertNull(saved.conflictBarcode)
        assertEquals("MANAGER_OVERRIDE", events(id).last())
    }

    @Test
    fun `given a conflict when shipped to home then it closes and is logged`() = runTest {
        val id = paidSale().also { reject(it, SyncStatus.CONFLICT) }

        repository.markShipToHome(id)

        assertEquals(SyncStatus.SHIP_TO_HOME, tx(id)!!.status)
        assertFalse(tx(id)!!.managerOverride)
        assertEquals("SHIP_TO_HOME", events(id).last())
    }

    @Test
    fun `given a conflict row then reason and barcode survive the round trip`() = runTest {
        val id = paidSale().also { reject(it, SyncStatus.CONFLICT) }

        val saved = tx(id)!!
        assertEquals("INVENTORY_EXHAUSTED", saved.statusReason)
        assertEquals("Hot Dog", saved.conflictItemName)
    }

    @Test
    fun `given synced and unsynced sales when pruning then only synced rows and their children go`() = runTest {
        val synced = List(2) { paidSale().also { dao.markSynced(it, "TXN-$it") } }
        val pending = paidSale()
        val conflict = paidSale().also { reject(it, SyncStatus.CONFLICT) }

        assertEquals(2, repository.pruneSynced())

        assertEquals(setOf(pending, conflict), repository.observeAll().first().map { it.id }.toSet())
        synced.forEach { assertTrue(events(it).isEmpty()) }
        assertEquals(0, repository.pruneSynced())
    }

    @Test
    fun `given sync pending is called then it goes straight to the scheduler`() = runTest {
        repository.syncPending()
        verify(exactly = 1) { given.scheduler.requestSync() }
    }

    @Test
    fun `given a logged event then it is appended in order with its detail`() = runTest {
        val id = paidSale()
        repository.logEvent(id, "NOTE", "first")
        repository.logEvent(id, "NOTE", null)

        val tail = repository.observeEvents(id).first().takeLast(2)
        assertEquals(listOf("first", null), tail.map { it.detail })
    }

    @Test
    fun `given a pending sale when status changes then the observed flow emits the update`() = runTest {
        val id = paidSale()
        repository.observeTransaction(id).test {
            assertEquals(SyncStatus.PENDING, awaitItem()!!.status)

            dao.markSynced(id, "TXN-9")

            val updated = awaitItem()!!
            assertEquals(SyncStatus.SYNCED, updated.status)
            assertEquals("TXN-9", updated.serverReference)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
