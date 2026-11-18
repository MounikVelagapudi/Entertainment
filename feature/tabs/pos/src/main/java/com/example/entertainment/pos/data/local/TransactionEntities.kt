package com.example.entertainment.pos.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.domain.model.TransactionEvent

@Entity(tableName = "pos_transactions", indices = [Index("status")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    /* Minted once at checkout and persisted, so every retry -- even after a process
       death or reboot -- resends the same key and the backend can dedupe. */
    val idempotencyKey: String,
    val totalCents: Long,
    val createdAtMillis: Long,
    val status: SyncStatus,
    val serverReference: String? = null,
    /* Backend error code for CONFLICT/FAILED, e.g. INVENTORY_EXHAUSTED. */
    val statusReason: String? = null,
    val conflictBarcode: String? = null,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus = PaymentStatus.NONE,
    val paymentReference: String? = null,
    val cardLast4: String? = null,
    /* Offline card auth waiting to be forwarded. Cleared once it is. */
    val offlinePaymentToken: String? = null,
    val managerOverride: Boolean = false,
)

@Entity(
    tableName = "pos_transaction_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")],
)
data class TransactionLineEntity(
    @PrimaryKey(autoGenerate = true) val lineId: Long = 0,
    val transactionId: String,
    val barcode: String,
    /* Snapshot at sale time -- catalog price changes mustn't rewrite history. */
    val name: String,
    val unitPriceCents: Long,
    val quantity: Int,
)

/* Append-only audit trail; what the timeline on the detail screen reads. */
@Entity(
    tableName = "pos_transaction_events",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")],
)
data class TransactionEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val transactionId: String,
    val atMillis: Long = System.currentTimeMillis(),
    val type: String,
    val detail: String? = null,
)

data class TransactionWithLines(
    @Embedded val header: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId")
    val lines: List<TransactionLineEntity>,
)

fun TransactionWithLines.toDomain() = Transaction(
    id = header.id,
    lines = lines.map {
        CartLine(Product(it.barcode, it.name, it.unitPriceCents), it.quantity)
    },
    totalCents = header.totalCents,
    createdAtMillis = header.createdAtMillis,
    status = header.status,
    serverReference = header.serverReference,
    statusReason = header.statusReason,
    conflictBarcode = header.conflictBarcode,
    paymentMethod = header.paymentMethod,
    paymentStatus = header.paymentStatus,
    paymentReference = header.paymentReference,
    cardLast4 = header.cardLast4,
    managerOverride = header.managerOverride,
)

fun TransactionEventEntity.toDomain() = TransactionEvent(atMillis, type, detail)
