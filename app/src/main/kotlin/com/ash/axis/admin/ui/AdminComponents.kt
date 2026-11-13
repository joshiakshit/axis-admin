package com.ash.axis.admin.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val ScreenPadding = 20.dp
internal val ItemSpacing = 12.dp
internal val CardPadding = 16.dp
internal val listPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 24.dp)

@Composable
internal fun StatusBadge(status: String) {
    val color by animateColorAsState(
        targetValue =
            when (status) {
                "approved", "allow", "APK ready" -> Color(0xFF34C759)
                "pending", "approve-all" -> Color(0xFFF59E0B)
                "banned", "ban", "No APK" -> Color(0xFFEF4444)
                "kick" -> Color(0xFFED6C02)
                "config-update" -> Color(0xFF6E90C0)
                else -> MaterialTheme.colorScheme.outline
            },
        label = "statusColor",
    )
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun ConfirmDialog(
    message: String,
    dismiss: () -> Unit,
    confirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Confirm action") },
        text = { Text(message) },
        confirmButton = { Button(onClick = confirm, shape = RoundedCornerShape(12.dp)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

internal fun displayTime(value: String): String =
    runCatching {
        java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    }.getOrDefault("Not available")
