package com.example.entertainment.pos.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.Transaction
import com.example.entertainment.pos.domain.model.TransactionEvent
import com.example.entertainment.pos.domain.model.formatCents

@Composable
fun TransactionDetailRoute(
    isReceipt: Boolean,
    onBack: () -> Unit,
    onNewSale: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val transaction by viewModel.transaction.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    TransactionDetailScreen(
        transaction = transaction,
        events = events,
        isReceipt = isReceipt,
        isResolving = isResolving,
        message = message,
        onBack = onBack,
        onNewSale = onNewSale,
        onVoidAndRefund = viewModel::onVoidAndRefund,
        onManagerOverride = viewModel::onManagerOverride,
        onShipToHome = viewModel::onShipToHome,
    )
}

@Composable
fun TransactionDetailScreen(
    transaction: Transaction?,
    events: List<TransactionEvent>,
    isReceipt: Boolean,
    isResolving: Boolean,
    message: String?,
    onBack: () -> Unit,
    onNewSale: () -> Unit,
    onVoidAndRefund: () -> Unit,
    onManagerOverride: () -> Unit,
    onShipToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transaction == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!isReceipt) TextButton(onClick = onBack) { Text("‹ Activity") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isReceipt) "Sale complete" else "Sale",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(transaction.status)
            }
            Text(
                "Local ID ${transaction.id.take(8)} · ${transaction.createdAtMillis.formatTime()}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(12.dp))
            SyncStatusCard(transaction, isResolving, onVoidAndRefund, onManagerOverride, onShipToHome)
            message?.let { Text(it, modifier = Modifier.padding(top = 6.dp)) }

            Spacer(Modifier.height(12.dp))
            PaymentCard(transaction)

            Spacer(Modifier.height(12.dp))
            transaction.lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${line.quantity} × ${line.product.name}")
                    Text(line.lineTotalCents.formatCents())
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(transaction.totalCents.formatCents(), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))
            Text("Timeline", style = MaterialTheme.typography.titleSmall)
            Timeline(events)
        }

        if (isReceipt) {
            Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth().testTag("pos_new_sale")) {
                Text("New sale")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncStatusCard(
    transaction: Transaction,
    isResolving: Boolean,
    onVoidAndRefund: () -> Unit,
    onManagerOverride: () -> Unit,
    onShipToHome: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, body) = when (transaction.status) {
        SyncStatus.AWAITING_PAYMENT -> colors.errorContainer to
            "Charge never completed on this device. Reconcile with the gateway using this sale's idempotency key " +
            "before retrying -- the card may or may not have been charged."
        SyncStatus.PENDING -> colors.tertiaryContainer to
            "Saved on device. Uploads automatically when online." +
            if (transaction.managerOverride) " Manager override applied." else ""
        SyncStatus.SYNCED -> colors.primaryContainer to "Server ref ${transaction.serverReference}"
        /* Customer has likely left with the goods -- these are compensating actions. */
        SyncStatus.CONFLICT -> colors.errorContainer to
            "${transaction.conflictItemName ?: "An item"} sold out while this terminal was offline " +
            "(${transaction.statusReason}). Pick how to make it right:"
        SyncStatus.FAILED -> colors.errorContainer to "Backend rejected this sale (${transaction.statusReason})."
        SyncStatus.VOIDED -> colors.surfaceVariant to "Voided and refunded after a stock conflict. Kept for audit."
        SyncStatus.SHIP_TO_HOME -> colors.surfaceVariant to "Conflict resolved: shipping from the distribution center."
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("pos_sync_status"),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (transaction.status == SyncStatus.CONFLICT) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedButton(onClick = onVoidAndRefund, enabled = !isResolving, modifier = Modifier.testTag("pos_void")) {
                        Text("Void & refund")
                    }
                    OutlinedButton(onClick = onShipToHome, enabled = !isResolving) { Text("Ship to home") }
                    OutlinedButton(onClick = onManagerOverride, enabled = !isResolving) { Text("Manager override") }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(transaction: Transaction) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Payment", style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(transaction.paymentMethod.label)
                    transaction.cardLast4?.let { append(" ···$it") }
                    append(" · ")
                    append(
                        when (transaction.paymentStatus) {
                            PaymentStatus.NONE -> "not charged"
                            PaymentStatus.APPROVED -> "approved"
                            PaymentStatus.STORED_OFFLINE -> "approved offline, not yet forwarded"
                            PaymentStatus.DECLINED -> "declined when forwarded"
                            PaymentStatus.REFUNDED -> "refunded"
                        }
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            transaction.paymentReference?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun Timeline(events: List<TransactionEvent>) {
    events.forEach { event ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                event.atMillis.formatTime(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(event.type, style = MaterialTheme.typography.labelLarge)
                event.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionDetailPreview() {
    MaterialTheme {
        TransactionDetailScreen(
            transaction = Transaction(
                id = "3f2a9c1e-0000-0000-0000-000000000000",
                lines = listOf(CartLine(Product("490000000016", "Hot Dog", 599), 1)),
                totalCents = 599,
                createdAtMillis = 0,
                status = SyncStatus.CONFLICT,
                serverReference = null,
                statusReason = "INVENTORY_EXHAUSTED",
                conflictBarcode = "490000000016",
                paymentMethod = PaymentMethod.CARD_TERMINAL,
                paymentStatus = PaymentStatus.APPROVED,
                paymentReference = "pi_3f2a9c1e0000",
                cardLast4 = "4242",
            ),
            events = listOf(
                TransactionEvent(0, "CREATED", "Card (terminal)"),
                TransactionEvent(1000, "PAYMENT_STORED_OFFLINE", "···4242"),
                TransactionEvent(9000, "CONFLICT", "INVENTORY_EXHAUSTED"),
            ),
            isReceipt = true,
            isResolving = false,
            message = null,
            onBack = {},
            onNewSale = {},
            onVoidAndRefund = {},
            onManagerOverride = {},
            onShipToHome = {},
        )
    }
}
