package com.example.entertainment.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao {

    @Insert
    protected abstract suspend fun insertHeader(header: TransactionEntity)

    @Insert
    protected abstract suspend fun insertLines(lines: List<TransactionLineEntity>)

    @Insert
    abstract suspend fun insertEvent(event: TransactionEventEntity)

    /* Header + lines or nothing -- the worker should never see a sale with no items. */
    @Transaction
    open suspend fun insertSale(header: TransactionEntity, lines: List<TransactionLineEntity>) {
        insertHeader(header)
        insertLines(lines)
        insertEvent(TransactionEventEntity(transactionId = header.id, type = "CREATED", detail = header.paymentMethod.label))
    }

    @Query("SELECT idempotencyKey FROM pos_transactions WHERE id = :id")
    abstract suspend fun idempotencyKeyOf(id: String): String?

    @Transaction
    @Query("SELECT * FROM pos_transactions WHERE status = :status ORDER BY createdAtMillis")
    abstract suspend fun getByStatus(status: SyncStatus): List<TransactionWithLines>

    @Transaction
    @Query("SELECT * FROM pos_transactions WHERE id = :id")
    abstract fun observe(id: String): Flow<TransactionWithLines?>

    @Transaction
    @Query("SELECT * FROM pos_transactions ORDER BY createdAtMillis DESC")
    abstract fun observeAll(): Flow<List<TransactionWithLines>>

    @Query("SELECT * FROM pos_transaction_events WHERE transactionId = :id ORDER BY eventId")
    abstract fun observeEvents(id: String): Flow<List<TransactionEventEntity>>

    @Query("SELECT COUNT(*) FROM pos_transactions WHERE status IN (:statuses)")
    abstract fun observeCount(statuses: List<SyncStatus>): Flow<Int>

    @Query(
        "UPDATE pos_transactions SET status = 'PENDING', paymentStatus = :paymentStatus, " +
            "paymentReference = :reference, cardLast4 = :last4, offlinePaymentToken = :offlineToken " +
            "WHERE id = :id AND status = 'AWAITING_PAYMENT'"
    )
    abstract suspend fun markPaid(id: String, paymentStatus: PaymentStatus, reference: String?, last4: String?, offlineToken: String?)

    @Query(
        "UPDATE pos_transactions SET paymentStatus = 'APPROVED', paymentReference = :reference, " +
            "offlinePaymentToken = NULL WHERE id = :id"
    )
    abstract suspend fun markOfflinePaymentForwarded(id: String, reference: String)

    @Query("DELETE FROM pos_transactions WHERE id = :id AND status = 'AWAITING_PAYMENT'")
    abstract suspend fun deleteDraft(id: String)

    @Query("UPDATE pos_transactions SET status = 'SYNCED', serverReference = :serverReference WHERE id = :id")
    abstract suspend fun markSynced(id: String, serverReference: String)

    @Query(
        "UPDATE pos_transactions SET status = :status, statusReason = :reason, conflictBarcode = :conflictBarcode " +
            "WHERE id = :id"
    )
    abstract suspend fun markRejected(id: String, status: SyncStatus, reason: String, conflictBarcode: String?)

    @Query("UPDATE pos_transactions SET paymentStatus = :paymentStatus WHERE id = :id")
    abstract suspend fun setPaymentStatus(id: String, paymentStatus: PaymentStatus)

    /* Guarded on CONFLICT -- never touch a sale that already synced or is still in flight. */
    @Query(
        "UPDATE pos_transactions SET status = 'VOIDED', paymentStatus = 'REFUNDED' " +
            "WHERE id = :id AND status = 'CONFLICT'"
    )
    abstract suspend fun markVoided(id: String): Int

    @Query(
        "UPDATE pos_transactions SET status = 'PENDING', managerOverride = 1, statusReason = NULL, " +
            "conflictBarcode = NULL WHERE id = :id AND status = 'CONFLICT'"
    )
    abstract suspend fun requeueWithOverride(id: String): Int

    @Query("UPDATE pos_transactions SET status = 'SHIP_TO_HOME' WHERE id = :id AND status = 'CONFLICT'")
    abstract suspend fun markShipToHome(id: String): Int

    @Query("DELETE FROM pos_transactions WHERE status = 'SYNCED'")
    abstract suspend fun deleteSynced(): Int
}
