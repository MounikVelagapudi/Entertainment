package com.example.entertainment.pos.data.remote

import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import com.example.entertainment.pos.testdata.TestProducts.POPCORN
import com.example.entertainment.pos.testdata.aMockBackend
import com.example.entertainment.pos.testdata.aTransactionItemDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MockBackendTest {

    private val backend = aMockBackend()

    @Test
    fun `given the injected constructor then defaults match the README`() {
        val defaults = MockBackend()
        assertEquals(800L, defaults.latencyMs.value)
        assertEquals(20, defaults.failureRatePercent.value)
        assertEquals(1, defaults.stockOf(HOT_DOG_BARCODE))
        assertEquals(99, defaults.stockOf(POPCORN.barcode))
    }

    @Test
    fun `given enough stock when reserving then it decrements and returns null`() {
        assertNull(backend.tryReserve(listOf(aTransactionItemDto(POPCORN.barcode, 3)), override = false))
        assertEquals(96, backend.stockOf(POPCORN.barcode))
    }

    @Test
    fun `given quantity above stock when reserving then that barcode is returned`() {
        assertEquals(HOT_DOG_BARCODE, backend.tryReserve(listOf(aTransactionItemDto(HOT_DOG_BARCODE, 2)), override = false))
        assertEquals(1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given one short item in a multi item sale then nothing is decremented`() {
        val short = backend.tryReserve(
            listOf(aTransactionItemDto(POPCORN.barcode, 1), aTransactionItemDto(HOT_DOG_BARCODE, 2)),
            override = false,
        )

        assertEquals(HOT_DOG_BARCODE, short)
        assertEquals(99, backend.stockOf(POPCORN.barcode))
        assertEquals(1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given several short items then the first one in the sale is reported`() {
        backend.adjustStock(POPCORN.barcode, -99)

        val short = backend.tryReserve(
            listOf(aTransactionItemDto(POPCORN.barcode, 1), aTransactionItemDto(HOT_DOG_BARCODE, 5)),
            override = false,
        )

        assertEquals(POPCORN.barcode, short)
    }

    @Test
    fun `given exact stock when reserving then it reaches zero`() {
        assertNull(backend.tryReserve(listOf(aTransactionItemDto(HOT_DOG_BARCODE, 1)), override = false))
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given override then stock can go negative`() {
        assertNull(backend.tryReserve(listOf(aTransactionItemDto(HOT_DOG_BARCODE, 3)), override = true))
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
        backend.tryReserve(
            listOf(aTransactionItemDto(HOT_DOG_BARCODE, 1), aTransactionItemDto(POPCORN.barcode, 10)),
            override = false,
        )

        backend.resetStock()

        assertEquals(mapOf(HOT_DOG_BARCODE to 1), backend.stock.value)
        assertEquals(99, backend.stockOf(POPCORN.barcode))
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
                if (backend.tryReserve(listOf(aTransactionItemDto(HOT_DOG_BARCODE, 1)), override = false) == null) {
                    wins.incrementAndGet()
                }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(1, wins.get())
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
    }
}
