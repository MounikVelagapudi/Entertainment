package com.example.entertainment.pos.domain.model

import com.example.entertainment.pos.presentation.PosUiState
import com.example.entertainment.pos.testdata.TestProducts.HOT_DOG
import com.example.entertainment.pos.testdata.TestProducts.POPCORN
import com.example.entertainment.pos.testdata.aCartLine
import com.example.entertainment.pos.testdata.aProduct
import com.example.entertainment.pos.testdata.aTransaction
import com.example.entertainment.pos.testdata.anApprovedPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosModelsTest {

    @Test
    fun `given a cart line then line total is unit price times quantity`() {
        assertEquals(899L, aCartLine(POPCORN, 1).lineTotalCents)
        assertEquals(1798L, aCartLine(POPCORN, 2).lineTotalCents)
        assertEquals(0L, aCartLine(POPCORN, 0).lineTotalCents)
    }

    @Test
    fun `given a huge quantity then the line total does not overflow int`() {
        val line = aCartLine(aProduct(name = "Bulk", priceCents = 1_000_000), 10_000)
        assertEquals(10_000_000_000L, line.lineTotalCents)
    }

    @Test
    fun `given cents then formatting pads to two decimals`() {
        assertEquals("$0.00", 0L.formatCents())
        assertEquals("$0.05", 5L.formatCents())
        assertEquals("$0.99", 99L.formatCents())
        assertEquals("$1.00", 100L.formatCents())
        assertEquals("$17.98", 1798L.formatCents())
        assertEquals("$1234.50", 123450L.formatCents())
    }

    @Test
    fun `given summed cents then there is no floating point drift`() {
        // 0.1 + 0.2 style trap: ten dimes in cents is exactly a dollar
        val dime = aProduct(barcode = "d", name = "Dime", priceCents = 10)
        val total = List(10) { aCartLine(dime, 1) }.sumOf { it.lineTotalCents }
        assertEquals("$1.00", total.formatCents())
    }

    @Test
    fun `given a conflict barcode in the lines then the item name is found`() {
        val tx = aTransaction(
            lines = listOf(aCartLine(POPCORN), aCartLine(HOT_DOG)),
            status = SyncStatus.CONFLICT,
            conflictBarcode = HOT_DOG.barcode,
        )
        assertEquals("Hot Dog", tx.conflictItemName)
    }

    @Test
    fun `given no conflict barcode or one not in the lines then the item name is null`() {
        assertNull(aTransaction(lines = listOf(aCartLine(POPCORN)), status = SyncStatus.CONFLICT).conflictItemName)
        assertNull(
            aTransaction(lines = listOf(aCartLine(POPCORN)), status = SyncStatus.CONFLICT, conflictBarcode = HOT_DOG.barcode)
                .conflictItemName
        )
    }

    @Test
    fun `given a new transaction then payment defaults to cash with nothing recorded`() {
        /* aTransaction leaves payment fields unset, so these are the model's own defaults. */
        val tx = aTransaction(status = SyncStatus.CONFLICT)
        assertEquals(PaymentMethod.CASH, tx.paymentMethod)
        assertEquals(PaymentStatus.NONE, tx.paymentStatus)
        assertNull(tx.paymentReference)
        assertFalse(tx.managerOverride)
    }

    @Test
    fun `given payment methods then only stripe online and paypal need the network`() {
        assertEquals(
            setOf(PaymentMethod.STRIPE_ONLINE, PaymentMethod.PAYPAL),
            PaymentMethod.entries.filter { it.requiresNetwork }.toSet(),
        )
    }

    @Test
    fun `given stripe test cards then last4 matches the published numbers`() {
        assertEquals("4242", TestCard.VISA_SUCCESS.last4)
        assertEquals("0002", TestCard.GENERIC_DECLINE.last4)
        assertEquals("9995", TestCard.INSUFFICIENT_FUNDS.last4)
        TestCard.entries.forEach { assertEquals(16, it.number.length) }
    }

    @Test
    fun `given a payment request then it defaults to the success card`() {
        /* Constructor on purpose: this checks the model's default, not a builder's. */
        assertEquals(TestCard.VISA_SUCCESS, PaymentRequest("s", "k", 100, PaymentMethod.CARD_TERMINAL).testCard)
    }

    @Test
    fun `given an approved result then it is online with no token by default`() {
        val approved = anApprovedPayment()
        assertFalse(approved.storedOffline)
        assertNull(approved.offlineToken)
    }

    @Test
    fun `given an empty ui state then totals are zero`() {
        val state = PosUiState()
        assertEquals(0L, state.totalCents)
        assertEquals(0, state.itemCount)
    }

    @Test
    fun `given a ui state with lines then totals and item count sum the cart`() {
        val state = PosUiState(cart = listOf(aCartLine(POPCORN, 2), aCartLine(HOT_DOG, 3)))
        assertEquals(899L * 2 + 599 * 3, state.totalCents)
        assertEquals(5, state.itemCount)
    }

    @Test
    fun `given offline then only methods that work without a network are available`() {
        val offline = PosUiState(isOnline = false)
        val online = PosUiState(isOnline = true)
        PaymentMethod.entries.forEach {
            assertTrue(online.isAvailable(it))
            assertEquals(it.name, !it.requiresNetwork, offline.isAvailable(it))
        }
    }
}
