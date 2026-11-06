package com.example.entertainment.pos.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.entertainment.pos.data.scanner.ScanSimulatorSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncLabScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncLabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Back to scan") }
        Text("Sync Lab", style = MaterialTheme.typography.headlineSmall)

        Section("Device") {
            OnlineIndicator(state.isOnline)
            Text("Toggle airplane mode to go offline.", style = MaterialTheme.typography.bodySmall)
        }

        Section("WorkManager · pos_sync") {
            Text(state.workStatus, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = viewModel::onSyncNow) { Text("Sync now") }
        }

        Section("Mock backend") {
            Text("503 rate on v1/transactions: ${state.failureRatePercent}%")
            Slider(
                value = state.failureRatePercent.toFloat(),
                onValueChange = { viewModel.onFailureRateChange(it.roundToInt()) },
                valueRange = 0f..100f,
                steps = 9,
            )
            Text("Latency: ${state.latencyMs} ms")
            Slider(
                value = state.latencyMs.toFloat(),
                onValueChange = { viewModel.onLatencyChange(it.toLong()) },
                valueRange = 0f..3000f,
                steps = 5,
            )
        }

        Section("Central stock") {
            Text(
                "Lower an item to 0 while offline, then sync -- that's the oversell conflict.",
                style = MaterialTheme.typography.bodySmall,
            )
            state.stock.forEach { (product, count) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(product.name, Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { viewModel.onAdjustStock(product.barcode, -1) }) { Text("−") }
                    Text("$count", Modifier.padding(horizontal = 8.dp))
                    FilledTonalIconButton(onClick = { viewModel.onAdjustStock(product.barcode, +1) }) { Text("+") }
                }
            }
            OutlinedButton(onClick = viewModel::onResetStock) { Text("Reset stock") }
        }

        Section("Scanner") {
            Text("Source: simulated. Camera (CameraX + ML Kit) not wired yet.", style = MaterialTheme.typography.bodySmall)
            Text("Next scans return:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.forcedBarcode == null,
                    onClick = { viewModel.onForceScan(null) },
                    label = { Text("Random") },
                )
                viewModel.catalog.products.forEach { product ->
                    FilterChip(
                        selected = state.forcedBarcode == product.barcode,
                        onClick = { viewModel.onForceScan(product.barcode) },
                        label = { Text(product.name) },
                    )
                }
                FilterChip(
                    selected = state.forcedBarcode == ScanSimulatorSettings.UNKNOWN_BARCODE,
                    onClick = { viewModel.onForceScan(ScanSimulatorSettings.UNKNOWN_BARCODE) },
                    label = { Text("Unknown") },
                )
            }
        }

        Section("Payments") {
            Text(
                "All four methods hit the mock v1/payments endpoint. Real Stripe Terminal / PaymentSheet / " +
                    "PayPal SDKs aren't wired -- see the module README.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
