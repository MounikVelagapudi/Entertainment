package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.MainDispatcherRule
import com.example.entertainment.pos.data.remote.MockBackend
import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import com.example.entertainment.pos.data.scanner.ScanSimulatorSettings
import com.example.entertainment.pos.data.sync.PosSyncScheduler
import com.example.entertainment.pos.domain.ProductCatalog
import com.example.entertainment.pos.fakes.FakeNetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncLabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val network = FakeNetworkMonitor()
    private val workStatus = MutableStateFlow("No sync scheduled yet")
    private val scheduler = mockk<PosSyncScheduler>(relaxed = true) {
        every { observeStatus() } returns workStatus
    }
    private val backend = MockBackend(latencyMs = 800, failureRatePercent = 20, initialStock = mapOf(HOT_DOG_BARCODE to 1), defaultStock = 99)
    private val scanSettings = ScanSimulatorSettings()
    private val catalog = ProductCatalog()

    private fun TestScope.viewModel() = SyncLabViewModel(network, scheduler, backend, scanSettings, catalog).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun SyncLabViewModel.stockOf(barcode: String) =
        uiState.value.stock.single { it.first.barcode == barcode }.second

    @Test
    fun `given default mock settings then state mirrors backend and lists every catalog item`() = runTest {
        val state = viewModel().uiState.value

        assertEquals(20, state.failureRatePercent)
        assertEquals(800L, state.latencyMs)
        assertEquals("No sync scheduled yet", state.workStatus)
        assertEquals(catalog.products, state.stock.map { it.first })
        assertEquals(1, state.stock.single { it.first.barcode == HOT_DOG_BARCODE }.second)
        assertEquals(99, state.stock.first().second)
        assertNull(state.forcedBarcode)
    }

    @Test
    fun `given failure rate changes then out of range values are clamped`() = runTest {
        val vm = viewModel()

        vm.onFailureRateChange(150); advanceUntilIdle()
        assertEquals(100, vm.uiState.value.failureRatePercent)

        vm.onFailureRateChange(-5); advanceUntilIdle()
        assertEquals(0, vm.uiState.value.failureRatePercent)
    }

    @Test
    fun `given a latency change then the backend and state pick it up`() = runTest {
        val vm = viewModel()
        vm.onLatencyChange(0); advanceUntilIdle()

        assertEquals(0L, backend.latencyMs.value)
        assertEquals(0L, vm.uiState.value.latencyMs)
    }

    @Test
    fun `given stock adjustments then the list updates and reset restores the seed`() = runTest {
        val vm = viewModel()

        vm.onAdjustStock(HOT_DOG_BARCODE, -1); advanceUntilIdle()
        assertEquals(0, vm.stockOf(HOT_DOG_BARCODE))

        vm.onAdjustStock(HOT_DOG_BARCODE, -1); advanceUntilIdle()
        assertEquals(0, vm.stockOf(HOT_DOG_BARCODE))

        vm.onAdjustStock("012345678905", 5); advanceUntilIdle()
        assertEquals(104, vm.stockOf("012345678905"))

        vm.onResetStock(); advanceUntilIdle()
        assertEquals(1, vm.stockOf(HOT_DOG_BARCODE))
        assertEquals(99, vm.stockOf("012345678905"))
    }

    @Test
    fun `given a forced scan then settings and state both carry it until cleared`() = runTest {
        val vm = viewModel()

        vm.onForceScan(HOT_DOG_BARCODE); advanceUntilIdle()
        assertEquals(HOT_DOG_BARCODE, scanSettings.forcedBarcode.value)
        assertEquals(HOT_DOG_BARCODE, vm.uiState.value.forcedBarcode)

        vm.onForceScan(null); advanceUntilIdle()
        assertNull(vm.uiState.value.forcedBarcode)
    }

    @Test
    fun `given worker and network changes then state follows them`() = runTest {
        val vm = viewModel()

        workStatus.value = "ENQUEUED (attempt 2) · 1 in chain"
        network.online.value = false
        advanceUntilIdle()

        assertEquals("ENQUEUED (attempt 2) · 1 in chain", vm.uiState.value.workStatus)
        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun `given sync now is tapped then the scheduler is asked once`() = runTest {
        viewModel().onSyncNow()
        verify(exactly = 1) { scheduler.requestSync() }
    }
}
