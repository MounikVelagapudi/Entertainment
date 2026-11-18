package com.example.entertainment.pos.data.scanner

import com.example.entertainment.pos.domain.BarcodeScanner
import com.example.entertainment.pos.domain.ProductCatalog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/* Lets Sync Lab pin what the next scans return -- random makes the hot dog conflict
   demo a coin flip otherwise. */
@Singleton
class ScanSimulatorSettings @Inject constructor() {
    private val _forcedBarcode = MutableStateFlow<String?>(null)
    val forcedBarcode: StateFlow<String?> = _forcedBarcode.asStateFlow()

    fun force(barcode: String?) { _forcedBarcode.value = barcode }

    companion object {
        const val UNKNOWN_BARCODE = "000000000000"
    }
}

/* Random catalog item after a short "aim" delay, occasionally a code we don't stock. */
class SimulatedBarcodeScanner @Inject constructor(
    private val catalog: ProductCatalog,
    private val settings: ScanSimulatorSettings,
) : BarcodeScanner {

    override suspend fun scan(): String {
        delay(SCAN_DELAY_MS)
        settings.forcedBarcode.value?.let { return it }
        return if (Random.nextInt(100) < UNKNOWN_RATE_PERCENT) ScanSimulatorSettings.UNKNOWN_BARCODE
        else catalog.products.random().barcode
    }

    private companion object {
        const val SCAN_DELAY_MS = 700L
        const val UNKNOWN_RATE_PERCENT = 10
    }
}
