package com.example.entertainment.pos.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.entertainment.pos.domain.ConflictResolver
import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.FakePosRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/* Real ConflictResolver over the fake repository and payment processor. */
class TransactionDetailViewModelBuilder {

    val repository = FakePosRepository()
    val payments = FakePaymentProcessor()

    fun withRefundResult(result: RefundResult) = apply { payments.nextRefund = result }

    /* Holds refunds mid-flight until the returned gate is completed. */
    fun holdRefunds(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { payments.gate = it }

    /* Opens the screen for [transactionId] and collects its flows like the screen would. */
    fun build(scope: TestScope, transactionId: String) = build(SavedStateHandle(mapOf(TransactionDetailViewModel.ARG_ID to transactionId))).also { vm ->
        scope.backgroundScope.launch { vm.transaction.collect {} }
        scope.backgroundScope.launch { vm.events.collect {} }
    }

    fun build(savedState: SavedStateHandle) =
        TransactionDetailViewModel(savedState, repository, ConflictResolver(repository, payments))
}
