package com.example.entertainment.pos.data.remote

import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MockBackendTest {

    private val backend = MockBackend(latencyMs = 0, failureRatePercent = 0, initialStock = mapOf(HOT_DOG_BARCODE to 1), defaultStock = 99)

    private fun item(barcode: String, qty: Int) = TransactionItemDto(barcode, "Item", 100, qty)

    @Test
    fun `given the injected constructor then defaults match the README`() {
        val defaults = MockBackend()
        assertEquals(800L, defaults.latencyMs.value)
        assertEquals(20, defaults.failureRatePercent.value)
        assertEquals(1, defaults.stockOf(HOT_DOG_BARCODE))
        assertEquals(99, defaults.stockOf("012345678905"))
    }

    @Test
    fun `given enough stock when reserving then it decrements and returns null`() {
        assertNull(backend.tryReserve(listOf(item("012345678905", 3)), override = false))
        assertEquals(96, backend.stockOf("012345678905"))
    }

    @Test
    fun `given quantity above stock when reserving then that barcode is returned`() {
        assertEquals(HOT_DOG_BARCODE, backend.tryReserve(listOf(item(HOT_DOG_BARCODE, 2)), override = false))
        assertEquals(1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given one short item in a multi item sale then nothing is decremented`() {
        val short = backend.tryReserve(listOf(item("012345678905", 1), item(HOT_DOG_BARCODE, 2)), override = false)

        assertEquals(HOT_DOG_BARCODE, short)
        assertEquals(99, backend.stockOf("012345678905"))
        assertEquals(1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given several short items then the first one in the sale is reported`() {
        backend.adjustStock("012345678905", -99)
        val short = backend.tryReserve(listOf(item("012345678905", 1), item(HOT_DOG_BARCODE, 5)), override = false)
        assertEquals("012345678905", short)
    }

    @Test
    fun `given exact stock when reserving then it reaches zero`() {
        assertNull(backend.tryReserve(listOf(item(HOT_DOG_BARCODE, 1)), override = false))
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given override then stock can go negative`() {
        assertNull(backend.tryReserve(listOf(item(HOT_DOG_BARCODE, 3)), override = true))
        assertEquals(-2, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given manual adjustments then stock never drops below zero`() {
        backend.adjustStock(HOT_DOG_BARCODE, -5)
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
        backend.adjustStock(HOT_DOG_BARCODE, 2)
        assertEquals(2, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given changed stock when reset then the initial seed is back`() {
        backend.tryReserve(listOf(item(HOT_DOG_BARCODE, 1), item("012345678905", 10)), override = false)
        backend.resetStock()

        assertEquals(mapOf(HOT_DOG_BARCODE to 1), backend.stock.value)
        assertEquals(99, backend.stockOf("012345678905"))
    }

    @Test
    fun `given failure rate input then it is clamped to 0-100`() {
        backend.setFailureRate(250)
        assertEquals(100, backend.failureRatePercent.value)
        backend.setFailureRate(-1)
        assertEquals(0, backend.failureRatePercent.value)
        backend.setFailureRate(35)
        assertEquals(35, backend.failureRatePercent.value)
    }

    @Test
    fun `given many terminals racing for the last hot dog then exactly one wins`() {
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val wins = AtomicInteger()
        repeat(threads) {
            pool.execute {
                start.await()
                if (backend.tryReserve(listOf(item(HOT_DOG_BARCODE, 1)), override = false) == null) wins.incrementAndGet()
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(1, wins.get())
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
    }
}
