package com.example.entertainment.pos.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.entertainment.pos.MainDispatcherRule
import com.example.entertainment.pos.domain.ConflictResolver
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.FakePosRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePosRepository()
    private val payments = FakePaymentProcessor()
    private val resolver = ConflictResolver(repository, payments)

    private suspend fun sale(status: SyncStatus = SyncStatus.CONFLICT): String {
        val draft = repository.createDraft(listOf(CartLine(Product("490000000016", "Hot Dog", 599), 2)), PaymentMethod.CARD_TERMINAL)
        repository.completePayment(draft.id, PaymentResult.Approved("pi_1", "4242"))
        repository.update(draft.id) { it.copy(status = status, conflictBarcode = "490000000016") }
        return draft.id
    }

    private fun TestScope.viewModel(id: String) =
        TransactionDetailViewModel(SavedStateHandle(mapOf(TransactionDetailViewModel.ARG_ID to id)), repository, resolver).also { vm ->
            backgroundScope.launch { vm.transaction.collect {} }
            backgroundScope.launch { vm.events.collect {} }
        }

    private fun status(id: String) = repository.saved.value.single { it.id == id }.status

    @Test(expected = IllegalStateException::class)
    fun `given no transaction id in saved state then construction fails`() {
        TransactionDetailViewModel(SavedStateHandle(), repository, resolver)
    }

    @Test
    fun `given a saved sale then the transaction is observed live`() = runTest {
        val id = sale(SyncStatus.PENDING)
        val vm = viewModel(id)
        advanceUntilIdle()
        assertEquals(SyncStatus.PENDING, vm.transaction.value!!.status)

        repository.update(id) { it.copy(status = SyncStatus.SYNCED, serverReference = "TXN-1") }
        advanceUntilIdle()

        assertEquals(SyncStatus.SYNCED, vm.transaction.value!!.status)
        assertEquals("TXN-1", vm.transaction.value!!.serverReference)
    }

    @Test
    fun `given a conflict then the item name is resolved from the conflicting barcode`() = runTest {
        val vm = viewModel(sale())
        advanceUntilIdle()
        assertEquals("Hot Dog", vm.transaction.value!!.conflictItemName)
    }

    @Test
    fun `given events logged for the sale then the timeline shows only its events`() = runTest {
        val id = sale()
        repository.logEvent("other", "NOISE")
        val vm = viewModel(id)

        repository.logEvent(id, "SYNC_ATTEMPT", "run 1")
        repository.logEvent(id, "CONFLICT", "INVENTORY_EXHAUSTED")
        advanceUntilIdle()

        assertEquals(listOf("SYNC_ATTEMPT", "CONFLICT"), vm.events.value.map { it.type })
        assertEquals("INVENTORY_EXHAUSTED", vm.events.value.last().detail)
    }

    @Test
    fun `given an unknown id then transaction stays null and actions are no-ops`() = runTest {
        val vm = viewModel("missing")
        advanceUntilIdle()

        vm.onVoidAndRefund(); advanceUntilIdle()

        assertNull(vm.transaction.value)
        assertFalse(vm.isResolving.value)
        assertTrue(payments.refunds.isEmpty())
    }

    @Test
    fun `given a conflict when void and refund succeeds then it is voided and the reference shown`() = runTest {
        val id = sale()
        val vm = viewModel(id)
        advanceUntilIdle()

        vm.onVoidAndRefund(); advanceUntilIdle()

        assertEquals(SyncStatus.VOIDED, status(id))
        assertEquals("Refunded (re_test)", vm.message.value)
        assertFalse(vm.isResolving.value)
    }

    @Test
    fun `given a conflict when the refund fails then the error shows and the sale stays in conflict`() = runTest {
        val id = sale()
        payments.nextRefund = RefundResult.Error("Refund failed (500)")
        val vm = viewModel(id)
        advanceUntilIdle()

        vm.onVoidAndRefund(); advanceUntilIdle()

        assertEquals(SyncStatus.CONFLICT, status(id))
        assertEquals("Refund failed (500)", vm.message.value)
    }

    @Test
    fun `given a resolution in flight then resolving is true and extra taps are ignored`() = runTest {
        val id = sale()
        payments.gate = CompletableDeferred()
        val vm = viewModel(id)
        advanceUntilIdle()

        vm.onVoidAndRefund(); advanceUntilIdle()
        assertTrue(vm.isResolving.value)
        vm.onVoidAndRefund()
        vm.onShipToHome()
        advanceUntilIdle()

        payments.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, payments.refunds.size)
        assertEquals(SyncStatus.VOIDED, status(id))
        assertFalse(vm.isResolving.value)
    }

    @Test
    fun `given an old message when a new resolution starts then the message is cleared`() = runTest {
        val id = sale()
        payments.nextRefund = RefundResult.Error("Network error, try again")
        val vm = viewModel(id)
        advanceUntilIdle()
        vm.onVoidAndRefund(); advanceUntilIdle()
        assertEquals("Network error, try again", vm.message.value)

        payments.gate = CompletableDeferred()
        vm.onVoidAndRefund(); advanceUntilIdle()
        assertNull(vm.message.value)
        payments.gate!!.complete(Unit); advanceUntilIdle()
    }

    @Test
    fun `given a conflict when manager override is used then it is requeued and a sync is asked for`() = runTest {
        val id = sale()
        val vm = viewModel(id)
        advanceUntilIdle()
        val before = repository.syncRequests

        vm.onManagerOverride(); advanceUntilIdle()

        assertEquals(SyncStatus.PENDING, vm.transaction.value!!.status)
        assertTrue(vm.transaction.value!!.managerOverride)
        assertEquals(before + 1, repository.syncRequests)
        assertFalse(vm.isResolving.value)
    }

    @Test
    fun `given a conflict when ship to home is used then the conflict closes without a refund`() = runTest {
        val id = sale()
        val vm = viewModel(id)
        advanceUntilIdle()

        vm.onShipToHome(); advanceUntilIdle()

        assertEquals(SyncStatus.SHIP_TO_HOME, vm.transaction.value!!.status)
        assertTrue(payments.refunds.isEmpty())
    }
}
