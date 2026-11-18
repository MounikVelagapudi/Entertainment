package com.example.entertainment.pos.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.TestCard
import com.example.entertainment.pos.domain.model.formatCents

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutScreen(
    state: PosUiState,
    onBack: () -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onTestCardSelected: (TestCard) -> Unit,
    onPayClick: () -> Unit,
    onPayPalApproved: () -> Unit,
    onPayPalCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !state.isPaying) { Text("‹ Back to scan") }
            OnlineIndicator(state.isOnline)
        }
        Text("Checkout", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(state.cart, key = { it.product.barcode }) { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${line.quantity} × ${line.product.name}")
                    Text(line.lineTotalCents.formatCents())
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Total", style = MaterialTheme.typography.titleLarge)
            Text(state.totalCents.formatCents(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Text("Payment method", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = state.paymentMethod == method,
                    onClick = { onPaymentMethodSelected(method) },
                    enabled = state.isAvailable(method) && !state.isPaying,
                    label = { Text(method.label) },
                    modifier = Modifier.testTag("pos_method_${method.name}"),
                )
            }
        }
        Text(
            text = methodHint(state),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (state.paymentMethod == PaymentMethod.CARD_TERMINAL || state.paymentMethod == PaymentMethod.STRIPE_ONLINE) {
            Text("Test card", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TestCard.entries.forEach { card ->
                    FilterChip(
                        selected = state.testCard == card,
                        onClick = { onTestCardSelected(card) },
                        enabled = !state.isPaying,
                        label = { Text(card.label) },
                    )
                }
            }
        }

        state.paymentError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
        }

        Button(
            onClick = onPayClick,
            enabled = state.cart.isNotEmpty() && !state.isPaying && state.isAvailable(state.paymentMethod),
            modifier = Modifier.fillMaxWidth().testTag("pos_pay"),
        ) {
            if (state.isPaying) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Pay ${state.totalCents.formatCents()} · ${state.paymentMethod.label}")
            }
        }
    }

    if (state.awaitingPayPalApproval) {
        /* Stands in for the PayPal approval sheet / browser redirect. */
        AlertDialog(
            onDismissRequest = onPayPalCancelled,
            title = { Text("PayPal (simulated)") },
            text = { Text("Buyer approves ${state.totalCents.formatCents()} to Entertainment Snacks?") },
            confirmButton = { Button(onClick = onPayPalApproved) { Text("Approve") } },
            dismissButton = { TextButton(onClick = onPayPalCancelled) { Text("Cancel") } },
        )
    }
}

private fun methodHint(state: PosUiState): String = when {
    !state.isOnline && state.paymentMethod == PaymentMethod.CARD_TERMINAL ->
        "Offline: card is approved on the terminal and forwarded when back online. A decline then can't be undone."
    !state.isOnline -> "Offline: Stripe online and PayPal need a connection."
    else -> when (state.paymentMethod) {
        PaymentMethod.CASH -> "No gateway. Refunds come out of the drawer."
        PaymentMethod.CARD_TERMINAL -> "Card-present (Stripe Terminal style). Works offline."
        PaymentMethod.STRIPE_ONLINE -> "Card-not-present (Stripe PaymentSheet style)."
        PaymentMethod.PAYPAL -> "Buyer approves in PayPal, then we capture."
    }
}

@Preview(showBackground = true)
@Composable
private fun CheckoutScreenPreview() {
    MaterialTheme {
        CheckoutScreen(
            state = PosUiState(
                cart = listOf(
                    CartLine(Product("1", "Popcorn (L)", 899), 2),
                    CartLine(Product("2", "Soda (M)", 499), 1),
                ),
                isOnline = false,
            ),
            onBack = {},
            onPaymentMethodSelected = {},
            onTestCardSelected = {},
            onPayClick = {},
            onPayPalApproved = {},
            onPayPalCancelled = {},
        )
    }
}
