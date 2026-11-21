package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.MainDispatcherRule
import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import com.example.entertainment.pos.testdata.TestProducts.POPCORN
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val given = SyncLabViewModelBuilder()

    private fun SyncLabViewModel.stockOf(barcode: String) =
        uiState.value.stock.single { it.first.barcode == barcode }.second

    @Test
    fun `given default mock settings then state mirrors backend and lists every catalog item`() = runTest {
        val state = given.build(this).uiState.value

        assertEquals(20, state.failureRatePercent)
        assertEquals(800L, state.latencyMs)
        assertEquals("No sync scheduled yet", state.workStatus)
        assertEquals(given.catalog.products, state.stock.map { it.first })
        assertEquals(1, state.stock.single { it.first.barcode == HOT_DOG_BARCODE }.second)
        assertEquals(99, state.stock.first().second)
        assertNull(state.forcedBarcode)
    }

    @Test
    fun `given failure rate changes then out of range values are clamped`() = runTest {
        val vm = given.build(this)

        vm.onFailureRateChange(150); advanceUntilIdle()
        assertEquals(100, vm.uiState.value.failureRatePercent)

        vm.onFailureRateChange(-5); advanceUntilIdle()
        assertEquals(0, vm.uiState.value.failureRatePercent)
    }

    @Test
    fun `given a latency change then the backend and state pick it up`() = runTest {
        val vm = given.build(this)

        vm.onLatencyChange(0); advanceUntilIdle()

        assertEquals(0L, given.backend.latencyMs.value)
        assertEquals(0L, vm.uiState.value.latencyMs)
    }

    @Test
    fun `given stock adjustments then the list updates and reset restores the seed`() = runTest {
        val vm = given.build(this)

        vm.onAdjustStock(HOT_DOG_BARCODE, -1); advanceUntilIdle()
        assertEquals(0, vm.stockOf(HOT_DOG_BARCODE))

        vm.onAdjustStock(HOT_DOG_BARCODE, -1); advanceUntilIdle()
        assertEquals(0, vm.stockOf(HOT_DOG_BARCODE))

        vm.onAdjustStock(POPCORN.barcode, 5); advanceUntilIdle()
        assertEquals(104, vm.stockOf(POPCORN.barcode))

        vm.onResetStock(); advanceUntilIdle()
        assertEquals(1, vm.stockOf(HOT_DOG_BARCODE))
        assertEquals(99, vm.stockOf(POPCORN.barcode))
    }

    @Test
    fun `given a forced scan then settings and state both carry it until cleared`() = runTest {
        val vm = given.build(this)

        vm.onForceScan(HOT_DOG_BARCODE); advanceUntilIdle()
        assertEquals(HOT_DOG_BARCODE, given.scanSettings.forcedBarcode.value)
        assertEquals(HOT_DOG_BARCODE, vm.uiState.value.forcedBarcode)

        vm.onForceScan(null); advanceUntilIdle()
        assertNull(vm.uiState.value.forcedBarcode)
    }

    @Test
    fun `given worker and network changes then state follows them`() = runTest {
        val vm = given.build(this)

        given.workStatus.value = "ENQUEUED (attempt 2) · 1 in chain"
        given.network.goOffline()
        advanceUntilIdle()

        assertEquals("ENQUEUED (attempt 2) · 1 in chain", vm.uiState.value.workStatus)
        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun `given sync now is tapped then the scheduler is asked once`() = runTest {
        given.build(this).onSyncNow()

        verify(exactly = 1) { given.scheduler.requestSync() }
    }
}
