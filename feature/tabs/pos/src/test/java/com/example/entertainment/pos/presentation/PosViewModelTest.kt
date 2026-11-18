package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.MainDispatcherRule
import com.example.entertainment.pos.domain.BarcodeScanner
import com.example.entertainment.pos.domain.ProductCatalog
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.TestCard
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.FakeNetworkMonitor
import com.example.entertainment.pos.fakes.FakePosRepository
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val catalog = ProductCatalog()
    private val popcorn = catalog.products.first()
    private val scanner = FakeScanner()
    private val repository = FakePosRepository()
    private val payments = FakePaymentProcessor()
    private val network = FakeNetworkMonitor()

    private fun TestScope.viewModel() = PosViewModel(scanner, catalog, repository, payments, network).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    private fun TestScope.scanPopcorn(vm: PosViewModel) {
        scanner.next = popcorn.barcode
        vm.onScanClick()
        advanceUntilIdle()
    }

    @Test
    fun `scanning a known barcode adds it to the cart`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        assertEquals(listOf(CartLine(popcorn, 1)), vm.uiState.value.cart)
    }

    @Test
    fun `scanning the same item twice bumps quantity instead of adding a row`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        scanPopcorn(vm)

        assertEquals(listOf(CartLine(popcorn, 2)), vm.uiState.value.cart)
        assertEquals(popcorn.priceCents * 2, vm.uiState.value.totalCents)
    }

    @Test
    fun `unknown barcode leaves cart alone and reports it`() = runTest {
        val vm = viewModel()
        scanner.next = "000000000000"
        vm.onScanClick(); advanceUntilIdle()

        assertTrue(vm.uiState.value.cart.isEmpty())
        assertTrue(vm.uiState.value.scanMessage!!.startsWith("Unknown item"))
    }

    @Test
    fun `manual entry goes through the same catalog lookup`() = runTest {
        val vm = viewModel()
        vm.onManualEntry(" ${popcorn.barcode} "); advanceUntilIdle()
        assertEquals(listOf(CartLine(popcorn, 1)), vm.uiState.value.cart)
    }

    @Test
    fun `decrementing to zero removes the line`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onDecrement(popcorn.barcode); advanceUntilIdle()
        assertTrue(vm.uiState.value.cart.isEmpty())
    }

    @Test
    fun `approved payment flips the draft to pending and navigates to receipt`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        val state = vm.uiState.value
        val saved = repository.saved.value.single()
        assertTrue(state.cart.isEmpty())
        assertEquals(SyncStatus.PENDING, saved.status)
        assertEquals(PaymentStatus.APPROVED, saved.paymentStatus)
        assertEquals(1, state.pendingSyncCount)
        assertEquals(saved.id, state.navigateToReceipt)

        vm.onReceiptNavigated(); advanceUntilIdle()
        assertNull(vm.uiState.value.navigateToReceipt)
    }

    @Test
    fun `payment idempotency key is derived from the sale key`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onTestCardSelected(TestCard.INSUFFICIENT_FUNDS)
        vm.onPayClick(); advanceUntilIdle()

        val request = payments.requests.single()
        assertEquals("key-tx-0:pay", request.idempotencyKey)
        assertEquals(TestCard.INSUFFICIENT_FUNDS, request.testCard)
    }

    @Test
    fun `declined card discards the draft and keeps the cart`() = runTest {
        val vm = viewModel()
        payments.nextResult = PaymentResult.Declined("insufficient_funds")
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        assertEquals(1, vm.uiState.value.cart.size)
        assertTrue(repository.saved.value.isEmpty())
        assertEquals("Declined: insufficient_funds", vm.uiState.value.paymentError)
        assertNull(vm.uiState.value.navigateToReceipt)
    }

    @Test
    fun `offline card terminal auth is saved as stored offline`() = runTest {
        val vm = viewModel()
        network.online.value = false
        payments.nextResult = PaymentResult.Approved(null, "4242", storedOffline = true, offlineToken = "4242424242424242")
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        assertEquals(PaymentStatus.STORED_OFFLINE, repository.saved.value.single().paymentStatus)
    }

    @Test
    fun `online-only methods are blocked while offline`() = runTest {
        val vm = viewModel()
        network.online.value = false
        scanPopcorn(vm)
        vm.onPaymentMethodSelected(PaymentMethod.STRIPE_ONLINE)
        vm.onPayClick(); advanceUntilIdle()

        assertTrue(payments.requests.isEmpty())
        assertTrue(repository.saved.value.isEmpty())
        assertEquals("Stripe online needs a connection", vm.uiState.value.paymentError)
    }

    @Test
    fun `paypal waits for buyer approval before charging`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onPaymentMethodSelected(PaymentMethod.PAYPAL)
        vm.onPayClick(); advanceUntilIdle()

        assertTrue(vm.uiState.value.awaitingPayPalApproval)
        assertTrue(payments.requests.isEmpty())

        vm.onPayPalApproved(); advanceUntilIdle()
        assertEquals(PaymentMethod.PAYPAL, payments.requests.single().method)
        assertEquals(SyncStatus.PENDING, repository.saved.value.single().status)
    }

    @Test
    fun `cancelling paypal charges nothing`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onPaymentMethodSelected(PaymentMethod.PAYPAL)
        vm.onPayClick(); advanceUntilIdle()
        vm.onPayPalCancelled(); advanceUntilIdle()

        assertTrue(payments.requests.isEmpty())
        assertEquals(1, vm.uiState.value.cart.size)
    }

    @Test
    fun `failed local save keeps the cart`() = runTest {
        val vm = viewModel()
        repository.failCreate = true
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        assertEquals(1, vm.uiState.value.cart.size)
        assertTrue(payments.requests.isEmpty())
    }

    @Test
    fun `opening the tab kicks a resync of leftovers`() = runTest {
        viewModel()
        assertEquals(1, repository.syncRequests)
    }

    @Test
    fun `given a scan in flight when scan is tapped again then the second tap is ignored`() = runTest {
        val vm = viewModel()
        scanner.gate = CompletableDeferred()
        scanner.next = popcorn.barcode

        vm.onScanClick(); advanceUntilIdle()
        assertTrue(vm.uiState.value.isScanning)
        vm.onScanClick(); advanceUntilIdle()

        scanner.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, scanner.scans)
        assertFalse(vm.uiState.value.isScanning)
        assertEquals(listOf(CartLine(popcorn, 1)), vm.uiState.value.cart)
    }

    @Test
    fun `given a previous scan message when a new scan starts then the message is cleared`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        assertEquals("Added ${popcorn.name}", vm.uiState.value.scanMessage)

        scanner.gate = CompletableDeferred()
        vm.onScanClick(); advanceUntilIdle()

        assertNull(vm.uiState.value.scanMessage)
    }

    @Test
    fun `given a blank manual entry when submitted then nothing happens`() = runTest {
        val vm = viewModel()
        vm.onManualEntry("   "); advanceUntilIdle()

        assertTrue(vm.uiState.value.cart.isEmpty())
        assertNull(vm.uiState.value.scanMessage)
    }

    @Test
    fun `given two products in the cart then total and item count add up across lines`() = runTest {
        val vm = viewModel()
        val ticket = catalog.lookup("885909950805")!!
        scanPopcorn(vm)
        scanPopcorn(vm)
        vm.onManualEntry(ticket.barcode); advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(CartLine(popcorn, 2), CartLine(ticket, 1)), state.cart)
        assertEquals(3, state.itemCount)
        assertEquals(899L * 2 + 1499, state.totalCents)
    }

    @Test
    fun `given an item in the cart when incremented then only that line changes`() = runTest {
        val vm = viewModel()
        val soda = catalog.lookup("042100005264")!!
        scanPopcorn(vm)
        vm.onManualEntry(soda.barcode); advanceUntilIdle()

        vm.onIncrement(soda.barcode); advanceUntilIdle()

        assertEquals(listOf(CartLine(popcorn, 1), CartLine(soda, 2)), vm.uiState.value.cart)
    }

    @Test
    fun `given a barcode not in the cart when incremented or decremented then the cart is unchanged`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)

        vm.onIncrement("885909950805")
        vm.onDecrement("885909950805")
        advanceUntilIdle()

        assertEquals(listOf(CartLine(popcorn, 1)), vm.uiState.value.cart)
    }

    @Test
    fun `given an empty cart when pay is tapped then no draft or charge is made`() = runTest {
        val vm = viewModel()
        vm.onPayClick(); advanceUntilIdle()

        assertTrue(repository.saved.value.isEmpty())
        assertTrue(payments.requests.isEmpty())
        assertFalse(vm.uiState.value.isPaying)
    }

    @Test
    fun `given a charge in flight when pay is tapped again then only one sale is created`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        payments.gate = CompletableDeferred()

        vm.onPayClick(); advanceUntilIdle()
        assertTrue(vm.uiState.value.isPaying)
        vm.onPayClick(); advanceUntilIdle()

        payments.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, payments.requests.size)
        assertEquals(1, repository.saved.value.size)
        assertFalse(vm.uiState.value.isPaying)
    }

    @Test
    fun `given a charge in flight then the draft is already saved as awaiting payment`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        payments.gate = CompletableDeferred()

        vm.onPayClick(); advanceUntilIdle()

        assertEquals(SyncStatus.AWAITING_PAYMENT, repository.saved.value.single().status)
        assertEquals(1, vm.uiState.value.needsAttentionCount)
        payments.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(0, vm.uiState.value.needsAttentionCount)
    }

    @Test
    fun `given a gateway error when paying then the draft is discarded and the error is shown`() = runTest {
        val vm = viewModel()
        payments.nextResult = PaymentResult.Error("Network error, try again")
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        assertTrue(repository.saved.value.isEmpty())
        assertEquals("Network error, try again", vm.uiState.value.paymentError)
        assertEquals(1, vm.uiState.value.cart.size)
        assertFalse(vm.uiState.value.isPaying)
    }

    @Test
    fun `given a failed local save then the error message asks for a retry`() = runTest {
        val vm = viewModel()
        repository.failCreate = true
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        assertEquals("Couldn't save sale, try again", vm.uiState.value.paymentError)
        assertFalse(vm.uiState.value.isPaying)
    }

    @Test
    fun `given a payment error when another method or card is picked then the error clears`() = runTest {
        val vm = viewModel()
        network.online.value = false
        scanPopcorn(vm)
        vm.onPaymentMethodSelected(PaymentMethod.PAYPAL)
        vm.onPayClick(); advanceUntilIdle()
        assertEquals("PayPal needs a connection", vm.uiState.value.paymentError)

        vm.onPaymentMethodSelected(PaymentMethod.CASH); advanceUntilIdle()
        assertNull(vm.uiState.value.paymentError)

        payments.nextResult = PaymentResult.Declined("generic_decline")
        vm.onPaymentMethodSelected(PaymentMethod.CARD_TERMINAL)
        vm.onPayClick(); advanceUntilIdle()
        vm.onTestCardSelected(TestCard.VISA_SUCCESS); advanceUntilIdle()
        assertNull(vm.uiState.value.paymentError)
    }

    @Test
    fun `given the device is offline when paying cash then the sale still goes through`() = runTest {
        val vm = viewModel()
        network.online.value = false
        payments.nextResult = PaymentResult.Approved("CASH-tx-0", null)
        scanPopcorn(vm)
        vm.onPaymentMethodSelected(PaymentMethod.CASH)
        vm.onPayClick(); advanceUntilIdle()

        val request = payments.requests.single()
        assertEquals(PaymentMethod.CASH, request.method)
        assertEquals(popcorn.priceCents, request.amountCents)
        assertEquals("tx-0", request.saleId)
        assertEquals(PaymentMethod.CASH, repository.saved.value.single().paymentMethod)
        assertEquals("tx-0", vm.uiState.value.navigateToReceipt)
    }

    @Test
    fun `given an approved sale then the scan message is cleared with the cart`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onPayClick(); advanceUntilIdle()

        assertNull(vm.uiState.value.scanMessage)
        assertNull(vm.uiState.value.paymentError)
    }

    @Test
    fun `given paypal is cancelled then the prompt closes with a cancel message`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)
        vm.onPaymentMethodSelected(PaymentMethod.PAYPAL)
        vm.onPayClick(); advanceUntilIdle()
        vm.onPayPalCancelled(); advanceUntilIdle()

        assertFalse(vm.uiState.value.awaitingPayPalApproval)
        assertEquals("Buyer cancelled PayPal", vm.uiState.value.paymentError)
    }

    @Test
    fun `given connectivity changes then ui state follows the network monitor`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isOnline)

        network.online.value = false; advanceUntilIdle()
        assertFalse(vm.uiState.value.isOnline)
        assertFalse(vm.uiState.value.isAvailable(PaymentMethod.STRIPE_ONLINE))
        assertTrue(vm.uiState.value.isAvailable(PaymentMethod.CARD_TERMINAL))
    }

    @Test
    fun `given conflicted and failed sales in the repo then the attention chip counts them`() = runTest {
        val vm = viewModel()
        repeat(3) { scanPopcorn(vm); vm.onPayClick(); advanceUntilIdle() }
        repository.update("tx-0") { it.copy(status = SyncStatus.CONFLICT) }
        repository.update("tx-1") { it.copy(status = SyncStatus.FAILED) }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.needsAttentionCount)
        assertEquals(1, vm.uiState.value.pendingSyncCount)
    }

    @Test
    fun `given a stream of ui states when a sale completes then receipt navigation fires once`() = runTest {
        val vm = viewModel()
        scanPopcorn(vm)

        vm.uiState.map { it.navigateToReceipt }.distinctUntilChanged().test {
            assertNull(awaitItem())
            vm.onPayClick()
            assertEquals("tx-0", awaitItem())
            vm.onReceiptNavigated()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeScanner : BarcodeScanner {
        var next = ""
        var scans = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun scan(): String {
            scans++
            gate?.await()
            return next
        }
    }
}
