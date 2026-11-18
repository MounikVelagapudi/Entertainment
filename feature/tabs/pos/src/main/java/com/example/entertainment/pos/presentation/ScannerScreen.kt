package com.example.entertainment.pos.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.formatCents

@Composable
fun ScannerScreen(
    state: PosUiState,
    onScanClick: () -> Unit,
    onManualEntry: (String) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onCheckoutClick: () -> Unit,
    onActivityClick: () -> Unit,
    onSyncLabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Point of Sale", style = MaterialTheme.typography.headlineSmall)
            OnlineIndicator(state.isOnline)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onActivityClick,
                label = {
                    Text(
                        buildString {
                            append("Activity")
                            if (state.pendingSyncCount > 0) append(" · ${state.pendingSyncCount} pending")
                            if (state.needsAttentionCount > 0) append(" · ${state.needsAttentionCount} need attention")
                        }
                    )
                },
                modifier = Modifier.testTag("pos_activity"),
            )
            TextButton(
                onClick = onSyncLabClick,
                modifier = Modifier.testTag("pos_sync_lab")
            ) { Text("Sync Lab") }
        }

        Spacer(Modifier.height(8.dp))
        Viewfinder(isScanning = state.isScanning)
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onScanClick,
                enabled = !state.isScanning,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pos_scan"),
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Scan item")
                }
            }
            ManualEntry(onManualEntry, Modifier.weight(1f))
        }

        state.scanMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        if (state.cart.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Cart is empty", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.cart, key = { it.product.barcode }) { line ->
                    CartRow(line, onIncrement, onDecrement)
                }
            }
        }

        Button(
            onClick = onCheckoutClick,
            enabled = state.cart.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pos_checkout"),
        ) {
            Text("Checkout · ${state.itemCount} items · ${state.totalCents.formatCents()}")
        }
    }
}

@Composable
private fun ManualEntry(onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var code by rememberSaveable { mutableStateOf("") }
    val submit = {
        onSubmit(code)
        code = ""
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit) },
            placeholder = { Text("Barcode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .weight(1f)
                .testTag("pos_manual_entry"),
        )
        OutlinedButton(
            onClick = submit,
            enabled = code.isNotBlank(),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text("Add")
        }
    }
}

/* Fake camera preview -- just a dark box with a sweeping line while "scanning". */
@Composable
private fun Viewfinder(isScanning: Boolean) {
    val sweep by rememberInfiniteTransition(label = "scanline").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanline",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF1B1B1F), RoundedCornerShape(12.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (isScanning) {
            Box(
                Modifier
                    .offset(y = (maxHeight - 2.dp) * sweep)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.Red)
            )
        } else {
            Text(
                "Simulated scanner · camera not wired",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CartRow(
    line: CartLine,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.product.name, style = MaterialTheme.typography.bodyLarge)
            Text(line.product.priceCents.formatCents(), style = MaterialTheme.typography.bodySmall)
        }
        FilledTonalIconButton(onClick = { onDecrement(line.product.barcode) }) { Text("−") }
        Text("${line.quantity}", modifier = Modifier.padding(horizontal = 8.dp))
        FilledTonalIconButton(onClick = { onIncrement(line.product.barcode) }) { Text("+") }
        Text(
            line.lineTotalCents.formatCents(),
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(min = 72.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScannerScreenPreview() {
    MaterialTheme {
        ScannerScreen(
            state = PosUiState(
                cart = listOf(CartLine(Product("1", "Popcorn (L)", 899), 2)),
                scanMessage = "Added Popcorn (L)",
                pendingSyncCount = 2,
                needsAttentionCount = 1,
                isOnline = false,
            ),
            onScanClick = {},
            onManualEntry = {},
            onIncrement = {},
            onDecrement = {},
            onCheckoutClick = {},
            onActivityClick = {},
            onSyncLabClick = {},
        )
    }
}
