package com.example.entertainment.pos.presentation

import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.TestCard

data class PosUiState(
    val cart: List<CartLine> = emptyList(),
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
    val isOnline: Boolean = true,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD_TERMINAL,
    val testCard: TestCard = TestCard.VISA_SUCCESS,
    val isPaying: Boolean = false,
    val paymentError: String? = null,
    /* PayPal's buyer-approval step -- a redirect/sheet in the real SDK. */
    val awaitingPayPalApproval: Boolean = false,
    val pendingSyncCount: Int = 0,
    /* CONFLICT + FAILED + stuck AWAITING_PAYMENT -- sales a person has to look at. */
    val needsAttentionCount: Int = 0,
    /* One-shot: set after a sale is saved, cleared once the screen has navigated. */
    val navigateToReceipt: String? = null,
) {
    val totalCents: Long get() = cart.sumOf { it.lineTotalCents }
    val itemCount: Int get() = cart.sumOf { it.quantity }

    fun isAvailable(method: PaymentMethod) = isOnline || !method.requiresNetwork
}
