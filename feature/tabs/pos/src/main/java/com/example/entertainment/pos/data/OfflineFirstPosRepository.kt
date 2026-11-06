package com.example.entertainment.pos.data

import com.example.entertainment.pos.data.local.TransactionDao
import com.example.entertainment.pos.data.local.TransactionEntity
import com.example.entertainment.pos.data.local.TransactionEventEntity
import com.example.entertainment.pos.data.local.TransactionLineEntity
import com.example.entertainment.pos.data.local.toDomain
import com.example.entertainment.pos.data.sync.PosSyncScheduler
import com.example.entertainment.pos.domain.PosRepository
import com.example.entertainment.pos.domain.SaleDraft
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.domain.model.TransactionEvent
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineFirstPosRepository @Inject constructor(
    private val dao: TransactionDao,
    private val syncScheduler: PosSyncScheduler,
) : PosRepository {

    override suspend fun createDraft(lines: List<CartLine>, method: PaymentMethod): SaleDraft {
        require(lines.isNotEmpty()) { "Empty cart" }

        val header = TransactionEntity(
            id = UUID.randomUUID().toString(),
            /* v4 UUID per sale, same as Stripe/Square/Adyen expect. Separate from the id
               so the key scheme can change without touching the primary key. */
            idempotencyKey = UUID.randomUUID().toString(),
            totalCents = lines.sumOf { it.lineTotalCents },
            createdAtMillis = System.currentTimeMillis(),
            status = SyncStatus.AWAITING_PAYMENT,
            paymentMethod = method,
        )
        val lineEntities = lines.map {
            TransactionLineEntity(
                transactionId = header.id,
                barcode = it.product.barcode,
                name = it.product.name,
                unitPriceCents = it.product.priceCents,
                quantity = it.quantity,
            )
        }
        dao.insertSale(header, lineEntities)
        return SaleDraft(header.id, header.idempotencyKey, header.totalCents)
    }

    override suspend fun completePayment(id: String, payment: PaymentResult.Approved) {
        val status = if (payment.storedOffline) PaymentStatus.STORED_OFFLINE else PaymentStatus.APPROVED
        dao.markPaid(id, status, payment.reference, payment.cardLast4, payment.offlineToken)
        logEvent(id, if (payment.storedOffline) "PAYMENT_STORED_OFFLINE" else "PAYMENT_APPROVED", payment.reference ?: payment.cardLast4?.let { "···$it" })

        /* Durable first, then schedule -- if we die in between, the next app open resyncs. */
        syncScheduler.requestSync()
    }

    override suspend fun discardDraft(id: String, reason: String) = dao.deleteDraft(id)

    override fun observeTransaction(id: String): Flow<Transaction?> =
        dao.observe(id).map { it?.toDomain() }

    override fun observeEvents(id: String): Flow<List<TransactionEvent>> =
        dao.observeEvents(id).map { events -> events.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePendingCount(): Flow<Int> = dao.observeCount(listOf(SyncStatus.PENDING))

    /* AWAITING_PAYMENT here means a crash mid-charge -- needs reconciling with the gateway. */
    override fun observeNeedsAttentionCount(): Flow<Int> =
        dao.observeCount(listOf(SyncStatus.CONFLICT, SyncStatus.FAILED, SyncStatus.AWAITING_PAYMENT))

    override suspend fun idempotencyKeyOf(id: String): String? = dao.idempotencyKeyOf(id)

    override suspend fun markRefundedAndVoided(id: String, refundReference: String) {
        if (dao.markVoided(id) > 0) logEvent(id, "VOIDED_AND_REFUNDED", refundReference)
    }

    override suspend fun requeueWithManagerOverride(id: String) {
        if (dao.requeueWithOverride(id) > 0) logEvent(id, "MANAGER_OVERRIDE", "Requeued, stock check skipped")
    }

    override suspend fun markShipToHome(id: String) {
        if (dao.markShipToHome(id) > 0) logEvent(id, "SHIP_TO_HOME", "Fulfilled from DC")
    }

    override suspend fun logEvent(id: String, type: String, detail: String?) =
        dao.insertEvent(TransactionEventEntity(transactionId = id, type = type, detail = detail))

    override fun syncPending() = syncScheduler.requestSync()

    /* Manual for now; for real this'd be a periodic worker with a retention window. */
    override suspend fun pruneSynced(): Int = dao.deleteSynced()
}
