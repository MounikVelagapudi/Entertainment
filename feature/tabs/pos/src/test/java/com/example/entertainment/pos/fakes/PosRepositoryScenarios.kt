package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.domain.PosRepository
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.testdata.aCartLine
import com.example.entertainment.pos.testdata.anApprovedPayment

/* Arrange steps shared by the repository, worker, resolver and ViewModel tests. */

/* Draft + approved payment, the same path a real checkout takes. Returns the sale id. */
suspend fun PosRepository.givenPaidSale(
    lines: List<CartLine> = listOf(aCartLine()),
    method: PaymentMethod = PaymentMethod.CARD_TERMINAL,
    payment: PaymentResult.Approved = anApprovedPayment(),
): String {
    val draft = createDraft(lines, method)
    completePayment(draft.id, payment)
    return draft.id
}

/* Paid sale forced into a status, e.g. a conflict the backend would have returned. */
suspend fun FakePosRepository.givenSale(
    status: SyncStatus,
    lines: List<CartLine> = listOf(aCartLine()),
    method: PaymentMethod = PaymentMethod.CARD_TERMINAL,
    payment: PaymentResult.Approved = anApprovedPayment(),
    statusReason: String? = null,
    conflictBarcode: String? = null,
): String {
    val id = givenPaidSale(lines, method, payment)
    update(id) {
        it.copy(
            status = status,
            statusReason = statusReason ?: it.statusReason,
            conflictBarcode = conflictBarcode ?: it.conflictBarcode,
        )
    }
    return id
}

fun FakePosRepository.givenTransactions(vararg transactions: Transaction) {
    saved.value = transactions.toList()
}

fun FakePosRepository.givenTransactions(transactions: List<Transaction>) {
    saved.value = transactions
}

fun FakePosRepository.transaction(id: String): Transaction = saved.value.single { it.id == id }
