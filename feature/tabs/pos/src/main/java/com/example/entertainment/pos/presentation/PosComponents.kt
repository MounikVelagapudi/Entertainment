package com.example.entertainment.pos.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.entertainment.pos.domain.model.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusChip(status: SyncStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        SyncStatus.SYNCED -> colors.primaryContainer to colors.onPrimaryContainer
        SyncStatus.PENDING -> colors.tertiaryContainer to colors.onTertiaryContainer
        SyncStatus.CONFLICT, SyncStatus.FAILED, SyncStatus.AWAITING_PAYMENT -> colors.errorContainer to colors.onErrorContainer
        SyncStatus.VOIDED, SyncStatus.SHIP_TO_HOME -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Text(
        text = status.name.replace('_', ' '),
        color = content,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun OnlineIndicator(isOnline: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(if (isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelMedium)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

fun Long.formatTime(): String = timeFormat.format(Date(this))
