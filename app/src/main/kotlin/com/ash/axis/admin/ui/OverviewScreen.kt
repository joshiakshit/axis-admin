package com.ash.axis.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Suppress("LongMethod")
internal fun DashboardScreen(
    state: AdminUiState,
    review: () -> Unit,
) {
    val health = state.health ?: return
    val stats = state.stats
    LazyColumn(
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pending requests", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.pendingCount == 0) "No one is waiting for access" else "Waiting for your review",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(state.pendingCount.toString(), style = MaterialTheme.typography.headlineLarge)
                    }
                    Button(onClick = review, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.pendingCount == 0) "View requests" else "Review requests")
                    }
                }
            }
        }
        item { ServiceCard(health.service, health.apkUploaded) }
        item {
            StatRow(
                left = StatValue("USERS", health.users.toString(), "Total accounts"),
                right = StatValue("PENDING", health.pending.toString(), "Need review", Color(0xFFF59E0B)),
            )
        }
        item {
            StatRow(
                left = StatValue("APPROVED", health.approved.toString(), "Can sign in", Color(0xFF34C759)),
                right = StatValue("BANNED", health.banned.toString(), "Access blocked", Color(0xFFEF4444)),
            )
        }
        state.healthUpdatedAt?.let { updated ->
            item {
                Text(
                    "Last refreshed ${displayTime(updated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.statsError?.let { error ->
            item {
                Text(
                    "Analytics: $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (stats != null) {
            item { SectionHeader("Activity") }
            item {
                ActivityRow(
                    listOf(
                        StatValue("24 HOURS", stats.activeLastDay.toString(), "Active"),
                        StatValue("7 DAYS", stats.activeLastWeek.toString(), "Active"),
                        StatValue("30 DAYS", stats.activeLastMonth.toString(), "Active"),
                    ),
                )
            }
            if (stats.versionDistribution.isNotEmpty()) {
                item { SectionHeader("Version distribution") }
                item { MetricList(stats.versionDistribution.map { it.version to it.count.toString() }) }
            }
            if (stats.deviceDistribution.isNotEmpty()) {
                item { SectionHeader("Top devices") }
                item { MetricList(stats.deviceDistribution.map { it.device to it.count.toString() }) }
            }
            if (stats.sdkDistribution.isNotEmpty()) {
                item { SectionHeader("Android SDK levels") }
                item { MetricList(stats.sdkDistribution.map { (if (it.sdk > 0) "API ${it.sdk}" else "Unknown") to it.count.toString() }) }
            }
        }
        if (health.metrics.isNotEmpty()) {
            item { SectionHeader("Aggregate metrics") }
            item {
                MetricList(
                    health.metrics.map {
                            (label, value) ->
                        label.replace('_', ' ').replaceFirstChar { it.uppercase() } to value.toString()
                    },
                )
            }
        }
    }
}

private data class StatValue(
    val label: String,
    val value: String,
    val detail: String,
    val tone: Color? = null,
)

@Composable
private fun ServiceCard(
    service: String,
    apkUploaded: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF34C759), RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SERVICE AVAILABLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(service, style = MaterialTheme.typography.titleMedium)
            }
            StatusBadge(if (apkUploaded) "APK ready" else "No APK")
        }
    }
}

@Composable
private fun StatRow(
    left: StatValue,
    right: StatValue,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CompactStatCard(left, Modifier.weight(1f))
        CompactStatCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun ActivityRow(stats: List<StatValue>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { CompactStatCard(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun CompactStatCard(
    stat: StatValue,
    modifier: Modifier = Modifier,
) {
    val tone = stat.tone ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(stat.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tone, letterSpacing = 0.8.sp)
            Text(
                stat.value,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stat.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MetricList(metrics: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 5.dp)) {
            metrics.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (index < metrics.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
