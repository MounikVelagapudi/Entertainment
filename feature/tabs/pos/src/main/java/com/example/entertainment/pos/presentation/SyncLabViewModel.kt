package com.example.entertainment.pos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.pos.data.remote.MockBackend
import com.example.entertainment.pos.data.scanner.ScanSimulatorSettings
import com.example.entertainment.pos.data.sync.PosSyncScheduler
import com.example.entertainment.pos.domain.NetworkMonitor
import com.example.entertainment.pos.domain.ProductCatalog
import com.example.entertainment.pos.domain.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SyncLabUiState(
    val isOnline: Boolean = true,
    val workStatus: String = "",
    val failureRatePercent: Int = 0,
    val latencyMs: Long = 0,
    val stock: List<Pair<Product, Int>> = emptyList(),
    val forcedBarcode: String? = null,
)

/**
 * Debug panel for watching the offline flow end to end. Talks to the mock backend and
 * simulator directly -- it's tooling, not a feature, so no domain layer in between.
 */
@HiltViewModel
class SyncLabViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    private val scheduler: PosSyncScheduler,
    private val backend: MockBackend,
    private val scanSettings: ScanSimulatorSettings,
    val catalog: ProductCatalog,
) : ViewModel() {

    private val mockState = combine(backend.failureRatePercent, backend.latencyMs, backend.stock) { rate, latency, _ ->
        Triple(rate, latency, catalog.products.map { it to backend.stockOf(it.barcode) })
    }

    val uiState: StateFlow<SyncLabUiState> =
        combine(networkMonitor.isOnline, scheduler.observeStatus(), mockState, scanSettings.forcedBarcode) { online, work, mock, forced ->
            SyncLabUiState(
                isOnline = online,
                workStatus = work,
                failureRatePercent = mock.first,
                latencyMs = mock.second,
                stock = mock.third,
                forcedBarcode = forced,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncLabUiState())

    fun onSyncNow() = scheduler.requestSync()

    fun onFailureRateChange(percent: Int) = backend.setFailureRate(percent)

    fun onLatencyChange(ms: Long) = backend.setLatency(ms)

    fun onAdjustStock(barcode: String, delta: Int) = backend.adjustStock(barcode, delta)

    fun onResetStock() = backend.resetStock()

    fun onForceScan(barcode: String?) = scanSettings.force(barcode)
}
