package com.example.entertainment.pos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.entertainment.pos.domain.BarcodeScanner
import com.example.entertainment.pos.domain.NetworkMonitor
import com.example.entertainment.pos.domain.PaymentProcessor
import com.example.entertainment.pos.domain.PosRepository
import com.example.entertainment.pos.domain.ProductCatalog
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentRequest
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.TestCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PosViewModel @Inject constructor(
    private val scanner: BarcodeScanner,
    private val catalog: ProductCatalog,
    private val repository: PosRepository,
    private val payments: PaymentProcessor,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val localState = MutableStateFlow(PosUiState())

    val uiState: StateFlow<PosUiState> =
        combine(
            localState,
            networkMonitor.isOnline,
            repository.observePendingCount(),
            repository.observeNeedsAttentionCount(),
        ) { state, online, pending, attention ->
            state.copy(isOnline = online, pendingSyncCount = pending, needsAttentionCount = attention)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PosUiState())

    init {
        repository.syncPending()
    }

    fun onScanClick() {
        if (localState.value.isScanning) return
        localState.update { it.copy(isScanning = true, scanMessage = null) }

        viewModelScope.launch {
            val barcode = scanner.scan()
            localState.update { it.copy(isScanning = false) }
            addToCart(barcode)
        }
    }

    /* Keyed entry -- what a cashier does when a label won't scan. */
    fun onManualEntry(barcode: String) {
        if (barcode.isNotBlank()) addToCart(barcode.trim())
    }

    fun onIncrement(barcode: String) = changeQuantity(barcode, +1)

    fun onDecrement(barcode: String) = changeQuantity(barcode, -1)

    fun onPaymentMethodSelected(method: PaymentMethod) =
        localState.update { it.copy(paymentMethod = method, paymentError = null) }

    fun onTestCardSelected(card: TestCard) = localState.update { it.copy(testCard = card, paymentError = null) }

    fun onPayClick() {
        /* User choices from localState -- uiState lags until combine re-emits. Only
           connectivity comes from uiState. */
        val state = localState.value.copy(isOnline = uiState.value.isOnline)
        if (state.cart.isEmpty() || state.isPaying) return
        if (!state.isAvailable(state.paymentMethod)) {
            localState.update { it.copy(paymentError = "${state.paymentMethod.label} needs a connection") }
            return
        }
        if (state.paymentMethod == PaymentMethod.PAYPAL) {
            localState.update { it.copy(awaitingPayPalApproval = true, paymentError = null) }
            return
        }
        pay(state)
    }

    fun onPayPalApproved() {
        localState.update { it.copy(awaitingPayPalApproval = false) }
        pay(localState.value)
    }

    fun onPayPalCancelled() =
        localState.update { it.copy(awaitingPayPalApproval = false, paymentError = "Buyer cancelled PayPal") }

    fun onReceiptNavigated() = localState.update { it.copy(navigateToReceipt = null) }

    private fun pay(state: PosUiState) {
        localState.update { it.copy(isPaying = true, paymentError = null) }

        viewModelScope.launch {
            try {
                /* Row first, charge second. If we die mid-charge there's an AWAITING_PAYMENT
                   row with the idempotency key to reconcile against -- never money with no record. */
                val draft = repository.createDraft(state.cart, state.paymentMethod)
                val request = PaymentRequest(
                    saleId = draft.id,
                    idempotencyKey = "${draft.idempotencyKey}:pay",
                    amountCents = draft.totalCents,
                    method = state.paymentMethod,
                    testCard = state.testCard,
                )

                when (val result = payments.authorize(request)) {
                    is PaymentResult.Approved -> {
                        repository.completePayment(draft.id, result)
                        localState.update {
                            it.copy(cart = emptyList(), isPaying = false, scanMessage = null, navigateToReceipt = draft.id)
                        }
                    }
                    is PaymentResult.Declined -> fail(draft.id, "Declined: ${result.reason}")
                    is PaymentResult.Error -> fail(draft.id, result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                /* Local write failed -- cart is kept so the cashier can just retry. */
                localState.update { it.copy(isPaying = false, paymentError = "Couldn't save sale, try again") }
            }
        }
    }

    private suspend fun fail(draftId: String, message: String) {
        repository.discardDraft(draftId, message)
        localState.update { it.copy(isPaying = false, paymentError = message) }
    }

    private fun addToCart(barcode: String) {
        val product = catalog.lookup(barcode)
        localState.update { state ->
            if (product == null) {
                state.copy(scanMessage = "Unknown item ($barcode)")
            } else {
                state.copy(
                    scanMessage = "Added ${product.name}",
                    cart = state.cart.withQuantityDelta(product.barcode, +1)
                        ?: (state.cart + CartLine(product, 1)),
                )
            }
        }
    }

    private fun changeQuantity(barcode: String, delta: Int) = localState.update { state ->
        state.cart.withQuantityDelta(barcode, delta)?.let { state.copy(cart = it) } ?: state
    }

    /* Null if the barcode isn't in the cart. Lines that hit zero are dropped. */
    private fun List<CartLine>.withQuantityDelta(barcode: String, delta: Int): List<CartLine>? {
        if (none { it.product.barcode == barcode }) return null
        return mapNotNull { line ->
            if (line.product.barcode != barcode) line
            else line.copy(quantity = line.quantity + delta).takeIf { it.quantity > 0 }
        }
    }
}
