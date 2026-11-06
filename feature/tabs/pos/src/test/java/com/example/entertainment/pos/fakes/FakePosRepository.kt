package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.domain.PosRepository
import com.example.entertainment.pos.domain.SaleDraft
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.domain.model.TransactionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePosRepository : PosRepository {
    val saved = MutableStateFlow<List<Transaction>>(emptyList())
    val events = mutableListOf<Pair<String, String>>()
    var failCreate = false
    var syncRequests = 0
    /* Ids idempotencyKeyOf should treat as missing. */
    val unknownIds = mutableSetOf<String>()
    val timeline = MutableStateFlow<List<Pair<String, TransactionEvent>>>(emptyList())

    override suspend fun createDraft(lines: List<CartLine>, method: PaymentMethod): SaleDraft {
        if (failCreate) error("disk full")
        val tx = Transaction(
            id = "tx-${saved.value.size}",
            lines = lines,
            totalCents = lines.sumOf { it.lineTotalCents },
            createdAtMillis = 0,
            status = SyncStatus.AWAITING_PAYMENT,
            serverReference = null,
            paymentMethod = method,
        )
        saved.value += tx
        return SaleDraft(tx.id, "key-${tx.id}", tx.totalCents)
    }

    override suspend fun completePayment(id: String, payment: PaymentResult.Approved) = update(id) {
        it.copy(
            status = SyncStatus.PENDING,
            paymentStatus = if (payment.storedOffline) PaymentStatus.STORED_OFFLINE else PaymentStatus.APPROVED,
            paymentReference = payment.reference,
            cardLast4 = payment.cardLast4,
        )
    }

    override suspend fun discardDraft(id: String, reason: String) {
        saved.value = saved.value.filterNot { it.id == id && it.status == SyncStatus.AWAITING_PAYMENT }
    }

    override fun observeTransaction(id: String): Flow<Transaction?> = saved.map { all -> all.find { it.id == id } }
    override fun observeEvents(id: String): Flow<List<TransactionEvent>> =
        timeline.map { all -> all.filter { it.first == id }.map { it.second } }
    override fun observeAll(): Flow<List<Transaction>> = saved

    override fun observePendingCount(): Flow<Int> = saved.map { all -> all.count { it.status == SyncStatus.PENDING } }

    override fun observeNeedsAttentionCount(): Flow<Int> = saved.map { all ->
        all.count { it.status in setOf(SyncStatus.CONFLICT, SyncStatus.FAILED, SyncStatus.AWAITING_PAYMENT) }
    }

    override suspend fun idempotencyKeyOf(id: String): String? = if (id in unknownIds) null else "key-$id"

    override suspend fun markRefundedAndVoided(id: String, refundReference: String) =
        update(id) { if (it.status == SyncStatus.CONFLICT) it.copy(status = SyncStatus.VOIDED, paymentStatus = PaymentStatus.REFUNDED) else it }

    override suspend fun requeueWithManagerOverride(id: String) =
        update(id) { if (it.status == SyncStatus.CONFLICT) it.copy(status = SyncStatus.PENDING, managerOverride = true) else it }

    override suspend fun markShipToHome(id: String) =
        update(id) { if (it.status == SyncStatus.CONFLICT) it.copy(status = SyncStatus.SHIP_TO_HOME) else it }

    override suspend fun logEvent(id: String, type: String, detail: String?) {
        events += id to type
        timeline.value += id to TransactionEvent(timeline.value.size.toLong(), type, detail)
    }

    override fun syncPending() {
        syncRequests++
    }

    override suspend fun pruneSynced(): Int {
        val before = saved.value.size
        saved.value = saved.value.filterNot { it.status == SyncStatus.SYNCED }
        return before - saved.value.size
    }

    fun update(id: String, transform: (Transaction) -> Transaction) {
        saved.value = saved.value.map { if (it.id == id) transform(it) else it }
    }
}
