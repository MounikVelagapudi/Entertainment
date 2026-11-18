package com.example.entertainment.pos.domain

import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.domain.model.TransactionEvent
import kotlinx.coroutines.flow.Flow

data class SaleDraft(val id: String, val idempotencyKey: String, val totalCents: Long)

interface PosRepository {
    /** Written before charging. Status AWAITING_PAYMENT -- the worker ignores it. */
    suspend fun createDraft(lines: List<CartLine>, method: PaymentMethod): SaleDraft

    /** Payment went through: flip to PENDING and schedule a sync. Never touches the network. */
    suspend fun completePayment(id: String, payment: PaymentResult.Approved)

    /** Declined/cancelled before any money moved -- nothing worth keeping. */
    suspend fun discardDraft(id: String, reason: String)

    fun observeTransaction(id: String): Flow<Transaction?>
    fun observeEvents(id: String): Flow<List<TransactionEvent>>
    fun observeAll(): Flow<List<Transaction>>
    fun observePendingCount(): Flow<Int>
    fun observeNeedsAttentionCount(): Flow<Int>

    suspend fun idempotencyKeyOf(id: String): String?
    suspend fun markRefundedAndVoided(id: String, refundReference: String)
    suspend fun requeueWithManagerOverride(id: String)
    suspend fun markShipToHome(id: String)
    suspend fun logEvent(id: String, type: String, detail: String? = null)

    /** Picks up anything left PENDING by a killed process or a worker that gave up. */
    fun syncPending()

    suspend fun pruneSynced(): Int
}
