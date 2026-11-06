package com.example.entertainment.pos.presentation

import app.cash.turbine.test
import com.example.entertainment.pos.MainDispatcherRule
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.fakes.FakePosRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePosRepository()

    private fun tx(id: String, status: SyncStatus) = Transaction(
        id = id,
        lines = listOf(CartLine(Product("012345678905", "Popcorn (L)", 899), 1)),
        totalCents = 899,
        createdAtMillis = 0,
        status = status,
        serverReference = null,
        paymentMethod = PaymentMethod.CASH,
    )

    private fun seedOneOfEach() {
        repository.saved.value = SyncStatus.entries.map { tx(it.name.lowercase(), it) }
    }

    private fun TestScope.viewModel() = ActivityViewModel(repository).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    private fun ActivityViewModel.ids() = uiState.value.transactions.map { it.id }

    @Test
    fun `given no filter then every sale is listed in repository order`() = runTest {
        seedOneOfEach()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ActivityFilter.ALL, vm.uiState.value.filter)
        assertEquals(SyncStatus.entries.map { it.name.lowercase() }, vm.ids())
    }

    @Test
    fun `given the attention filter then conflicts failures and stuck drafts are shown`() = runTest {
        seedOneOfEach()
        val vm = viewModel()
        vm.onFilterSelected(ActivityFilter.ATTENTION); advanceUntilIdle()

        assertEquals(ActivityFilter.ATTENTION, vm.uiState.value.filter)
        assertEquals(setOf("awaiting_payment", "conflict", "failed"), vm.ids().toSet())
    }

    @Test
    fun `given each narrow filter then only matching statuses come through`() = runTest {
        seedOneOfEach()
        val vm = viewModel()

        vm.onFilterSelected(ActivityFilter.PENDING); advanceUntilIdle()
        assertEquals(listOf("pending"), vm.ids())

        vm.onFilterSelected(ActivityFilter.SYNCED); advanceUntilIdle()
        assertEquals(listOf("synced"), vm.ids())

        vm.onFilterSelected(ActivityFilter.RESOLVED); advanceUntilIdle()
        assertEquals(setOf("voided", "ship_to_home"), vm.ids().toSet())
    }

    @Test
    fun `given any sync status then exactly one narrow filter matches it`() {
        val narrow = ActivityFilter.entries.filter { it != ActivityFilter.ALL }
        SyncStatus.entries.forEach { status ->
            assertEquals(status.name, 1, narrow.count { status in it.statuses!! })
        }
    }

    @Test
    fun `given a filter is active when the repo changes then the list updates live`() = runTest {
        val vm = viewModel()
        vm.onFilterSelected(ActivityFilter.PENDING); advanceUntilIdle()
        assertTrue(vm.ids().isEmpty())

        repository.saved.value = listOf(tx("a", SyncStatus.PENDING)); advanceUntilIdle()
        assertEquals(listOf("a"), vm.ids())

        repository.update("a") { it.copy(status = SyncStatus.SYNCED) }; advanceUntilIdle()
        assertTrue(vm.ids().isEmpty())
    }

    @Test
    fun `given sync now is tapped then the repo is asked to sync and a message shows`() = runTest {
        val vm = viewModel()
        val before = repository.syncRequests

        vm.onSyncNow(); advanceUntilIdle()

        assertEquals(before + 1, repository.syncRequests)
        assertEquals("Sync requested -- runs once online", vm.uiState.value.message)
    }

    @Test
    fun `given synced sales when pruned then only synced rows go and the count is reported`() = runTest {
        repository.saved.value = listOf(
            tx("a", SyncStatus.SYNCED),
            tx("b", SyncStatus.SYNCED),
            tx("c", SyncStatus.CONFLICT),
        )
        val vm = viewModel()

        vm.onPruneSynced(); advanceUntilIdle()

        assertEquals("Removed 2 synced sales", vm.uiState.value.message)
        assertEquals(listOf("c"), vm.ids())
    }

    @Test
    fun `given nothing to prune then the message says zero`() = runTest {
        val vm = viewModel()
        vm.onPruneSynced(); advanceUntilIdle()
        assertEquals("Removed 0 synced sales", vm.uiState.value.message)
    }

    @Test
    fun `given a fresh screen then the first emitted state is empty with no message`() = runTest {
        ActivityViewModel(repository).uiState.test {
            val first = awaitItem()
            assertTrue(first.transactions.isEmpty())
            assertNull(first.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
