package com.example.entertainment.pos.domain

import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import javax.inject.Inject

/**
 * What an associate can do with a CONFLICT. The customer is usually gone with the goods,
 * so these are compensating actions, not rollbacks -- the original row always stays.
 */
class ConflictResolver @Inject constructor(
    private val repository: PosRepository,
    private val payments: PaymentProcessor,
) {

    /** Refund first; only void once the money is actually back. */
    suspend fun voidAndRefund(transaction: Transaction): RefundResult {
        check(transaction.status == SyncStatus.CONFLICT) { "Only conflicts can be voided" }
        val key = repository.idempotencyKeyOf(transaction.id) ?: error("Unknown sale")

        val result = payments.refund(
            method = transaction.paymentMethod,
            paymentReference = transaction.paymentReference,
            amountCents = transaction.totalCents,
            idempotencyKey = "$key:refund",
        )
        when (result) {
            is RefundResult.Refunded -> repository.markRefundedAndVoided(transaction.id, result.reference)
            is RefundResult.Error -> repository.logEvent(transaction.id, "REFUND_FAILED", result.message)
        }
        return result
    }

    /* Stock physically found (endcap, back room) but central inventory says 0. */
    suspend fun managerOverride(transaction: Transaction) {
        repository.requeueWithManagerOverride(transaction.id)
        repository.syncPending()
    }

    /* Real version posts a fulfillment order; here it just closes the conflict. */
    suspend fun shipToHome(transaction: Transaction) = repository.markShipToHome(transaction.id)
}
