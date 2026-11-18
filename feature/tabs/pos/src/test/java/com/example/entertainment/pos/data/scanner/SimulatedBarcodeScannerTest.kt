package com.example.entertainment.pos.data.scanner

import com.example.entertainment.pos.domain.ProductCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedBarcodeScannerTest {

    private val catalog = ProductCatalog()
    private val settings = ScanSimulatorSettings()
    private val scanner = SimulatedBarcodeScanner(catalog, settings)

    @Test
    fun `given a scan then it takes about 700ms of aim time`() = runTest {
        val scan = async { scanner.scan() }

        advanceTimeBy(699); runCurrent()
        assertFalse(scan.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertTrue(scan.isCompleted)
    }

    @Test
    fun `given a forced barcode then every scan returns it`() = runTest {
        settings.force("490000000016")
        repeat(20) { assertEquals("490000000016", scanner.scan()) }
    }

    @Test
    fun `given a forced unknown code then it is passed through as is`() = runTest {
        settings.force(ScanSimulatorSettings.UNKNOWN_BARCODE)
        assertEquals("000000000000", scanner.scan())
    }

    @Test
    fun `given the force is cleared then scans go back to random`() = runTest {
        settings.force("490000000016")
        settings.force(null)
        val seen = List(200) { scanner.scan() }.toSet()
        assertTrue(seen.size > 1)
    }

    @Test
    fun `given random scans then results are catalog items or the unknown code`() = runTest {
        val valid = catalog.products.map { it.barcode }.toSet() + ScanSimulatorSettings.UNKNOWN_BARCODE
        val results = List(1_000) { scanner.scan() }

        assertTrue(results.all { it in valid })
        // ~10% unknown; wide band so it doesn't flake
        val unknown = results.count { it == ScanSimulatorSettings.UNKNOWN_BARCODE }
        assertTrue("got $unknown", unknown in 40..200)
        assertTrue(results.toSet().containsAll(catalog.products.map { it.barcode }))
    }
}
