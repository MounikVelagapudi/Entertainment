package com.example.profile.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Session Lab card: real TMDB guest-session calls plus controls that trigger
 * plan A (proactive) and plan B (reactive 401) refresh.
 */
@Composable
fun SessionLabSection(
    modifier: Modifier = Modifier,
    viewModel: SessionLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(modifier = modifier.fillMaxWidth().testTag("session_lab")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Session Lab: TMDB guest session", style = MaterialTheme.typography.titleMedium)
            Text("Session: ${state.sessionLabel}")
            Text(
                text = "Expires in: ${state.remainingLabel}" +
                    if (state.insideRefreshWindow) "  (inside 60s window, next call refreshes)" else "",
                color = if (state.insideRefreshWindow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )

            Text("Real API calls", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::rateMovie, enabled = !state.busy) { Text("Rate Fight Club") }
                Button(onClick = viewModel::loadRated, enabled = !state.busy) { Text("Load my ratings") }
            }

            Text("Put the session in a state, then make a call", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::expireSoon) { Text("A: expire in 45s") }
                OutlinedButton(onClick = viewModel::revokeOnServer) { Text("B: revoke on server") }
                OutlinedButton(onClick = viewModel::reset) { Text("Reset") }
            }

            Text(state.lastResult, style = MaterialTheme.typography.bodyMedium)

            if (state.events.isNotEmpty()) {
                Text("What happened (newest first)", style = MaterialTheme.typography.labelLarge)
                state.events.forEach { row ->
                    Text(row, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
