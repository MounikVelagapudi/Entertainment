package com.example.entertainment.pos.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Server-side state for [MockPosInterceptor], exposed so the Sync Lab screen can tweak
 * it at runtime. In memory only -- resets with the process.
 */
@Singleton
class MockBackend(
    latencyMs: Long,
    failureRatePercent: Int,
    private val initialStock: Map<String, Int>,
    private val defaultStock: Int,
) {

    @Inject
    constructor() : this(
        latencyMs = 800L,
        failureRatePercent = 20,
        initialStock = mapOf(HOT_DOG_BARCODE to 1),
        defaultStock = 99,
    )

    private val _latencyMs = MutableStateFlow(latencyMs)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _failureRatePercent = MutableStateFlow(failureRatePercent)
    val failureRatePercent: StateFlow<Int> = _failureRatePercent.asStateFlow()

    private val _stock = MutableStateFlow(initialStock)
    /* Only barcodes that differ from defaultStock are listed. */
    val stock: StateFlow<Map<String, Int>> = _stock.asStateFlow()

    fun setLatency(ms: Long) { _latencyMs.value = ms }

    fun setFailureRate(percent: Int) { _failureRatePercent.value = percent.coerceIn(0, 100) }

    fun stockOf(barcode: String): Int = _stock.value[barcode] ?: defaultStock

    @Synchronized
    fun adjustStock(barcode: String, delta: Int) {
        _stock.value = _stock.value + (barcode to (stockOf(barcode) + delta).coerceAtLeast(0))
    }

    @Synchronized
    fun resetStock() { _stock.value = initialStock }

    /**
     * Check-and-decrement in one step, so two terminals syncing at once can't both
     * take the last unit. Returns the first barcode that's short, or null if reserved.
     * Override lets stock go negative -- HQ reconciles the count later.
     */
    @Synchronized
    fun tryReserve(items: List<TransactionItemDto>, override: Boolean): String? {
        if (!override) {
            items.firstOrNull { stockOf(it.barcode) < it.quantity }?.let { return it.barcode }
        }
        _stock.value = _stock.value + items.associate { it.barcode to stockOf(it.barcode) - it.quantity }
        return null
    }

    companion object {
        const val HOT_DOG_BARCODE = "490000000016"
    }
}
