package com.sentinel.monitor.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.service.AutomationProgress
import com.sentinel.monitor.ui.components.AutomationBannerCard
import com.sentinel.monitor.ui.components.TelemetryGaugeCard
import com.sentinel.monitor.ui.components.TelemetrySparkline
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import java.util.Locale

@Composable
fun VitalsTab(
    snapshot: SystemSnapshot,
    liveRamSparkline: List<Float>,
    liveCpuSparkline: List<Float>,
    isSweeping: Boolean,
    sweepBanner: String?,
    automationProgress: AutomationProgress,
    nonEssentialRunningCount: Int,
    reclaimableCacheBytes: Long,
    isMiniHudRunning: Boolean,
    protectedCount: Int,
    onRunMemorySweep: () -> Unit,
    onStopNonEssentialProcesses: () -> Unit,
    onAutoClearAllCaches: () -> Unit,
    onCancelAutomation: () -> Unit,
    onDismissAutomationBanner: () -> Unit,
    onToggleMiniHud: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ramRag = TelemetryRag.fromUsageFraction(snapshot.ramUsageFraction)
    val storageRag = TelemetryRag.fromUsageFraction(snapshot.storageUsageFraction)
    val thermalRag = TelemetryRag.fromBatteryTempCelsius(snapshot.batteryTempCelsius)
    val cpuRag = TelemetryRag.fromUsageFraction(snapshot.avgCpuUtilizationFraction)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (automationProgress.isRunning || automationProgress.lastSummaryBanner != null) {
            item {
                AutomationBannerCard(
                    progress = automationProgress,
                    onCancel = onCancelAutomation,
                    onDismissSummary = onDismissAutomationBanner
                )
            }
        }

        // 1. Direct Process, Memory & Cache Control Center
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SentinelEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = SentinelEmerald
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1-Tap Process & Cache Control",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val wlNote = if (protectedCount > 0) " • $protectedCount protected" else ""
                            Text(
                                text = "$nonEssentialRunningCount active non-essential apps$wlNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Primary Row: Stop Non-Essential Processes + Clear App Caches
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onStopNonEssentialProcesses,
                            enabled = !automationProgress.isRunning,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop Apps ($nonEssentialRunningCount)")
                        }
                        Button(
                            onClick = onAutoClearAllCaches,
                            enabled = !automationProgress.isRunning,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SentinelAmber),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            val cacheLbl = if (reclaimableCacheBytes >= 1024 * 1024L) {
                                "Clear (${TelemetryFormatters.formatBytes(reclaimableCacheBytes)})"
                            } else {
                                "Clear Cache"
                            }
                            Text(cacheLbl, maxLines = 1)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Secondary Row: Instant Cached RAM Sweep + Floating Mini-HUD
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRunMemorySweep,
                            enabled = !isSweeping,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = SentinelEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSweeping) "Sweeping…" else "Quick RAM Sweep")
                        }
                        OutlinedButton(
                            onClick = onToggleMiniHud,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = null,
                                tint = if (isMiniHudRunning) SentinelEmerald else SentinelCyan,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isMiniHudRunning) "Stop HUD" else "Mini-HUD")
                        }
                    }

                    if (sweepBanner != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "✓ $sweepBanner",
                            style = MaterialTheme.typography.labelMedium,
                            color = SentinelEmerald
                        )
                    }
                }
            }
        }

        // 2. RAM + ZRAM / Swap & Cached Breakdown
        item {
            val zramText = if (snapshot.swapTotalBytes > 0L) {
                "ZRAM/Swap: ${TelemetryFormatters.formatBytes(snapshot.swapUsedBytes)} / ${TelemetryFormatters.formatBytes(snapshot.swapTotalBytes)} • Cached: ${TelemetryFormatters.formatBytes(snapshot.cachedRamBytes)}"
            } else {
                "Kernel Page Cache: ${TelemetryFormatters.formatBytes(snapshot.cachedRamBytes)}"
            }
            TelemetryGaugeCard(
                title = "RAM & ZRAM Pressure",
                icon = Icons.Default.Memory,
                headline = "${(snapshot.ramUsageFraction * 100).toInt()}% Used (${TelemetryFormatters.formatBytes(snapshot.availableRamBytes)} Free)",
                subtitle = "${TelemetryFormatters.formatBytes(snapshot.usedRamBytes)} of ${TelemetryFormatters.formatBytes(snapshot.totalRamBytes)} physical RAM",
                progress = snapshot.ramUsageFraction,
                rag = ramRag,
                sparklineValues = liveRamSparkline,
                extraContent = {
                    Text(
                        text = zramText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // 3. Per-Core Live CPU Clocks & Display Refresh HUD
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = SentinelCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "CPU Clusters & Display",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${snapshot.displayRefreshHz} Hz • ${snapshot.cpuGovernor}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SentinelCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val headlineText = if (snapshot.peakCpuMhz > 0) {
                        "${(snapshot.avgCpuUtilizationFraction * 100).toInt()}% Scale • Peak ${snapshot.peakCpuMhz} MHz"
                    } else {
                        "${snapshot.cpuCoreCount}-Core ${snapshot.socModel}"
                    }
                    Text(
                        text = headlineText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (liveCpuSparkline.size >= 2 && snapshot.peakCpuMhz > 0) {
                        Spacer(Modifier.height(10.dp))
                        TelemetrySparkline(values = liveCpuSparkline, color = SentinelCyan)
                    }
                    Spacer(Modifier.height(12.dp))
                    val rows = snapshot.cpuCores.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                pair.forEach { core ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "CPU ${core.coreIndex}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (core.curFreqMhz > 0) "${core.curFreqMhz} MHz" else "Protected",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { core.utilizationFraction },
                                            color = if (core.utilizationFraction > 0.8f) cpuRag.let { SentinelEmerald } else SentinelCyan,
                                            trackColor = SentinelSurfaceHigh,
                                            strokeCap = StrokeCap.Round,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 4. Power, Live Wattage (W) & Thermal Throttling Envelope
        item {
            val wattsStr = TelemetryFormatters.formatWatts(snapshot.batteryPowerWatts, snapshot.isCharging)
            val cycleSuffix = if (snapshot.batteryCycleCount >= 0) " • ${snapshot.batteryCycleCount} cycles" else ""
            TelemetryGaugeCard(
                title = "Power, Wattage & Thermal",
                icon = Icons.Default.BatteryChargingFull,
                headline = String.format(
                    Locale.US,
                    "%d%% • %s • %.1f°C",
                    snapshot.batteryPercent,
                    wattsStr,
                    snapshot.batteryTempCelsius
                ),
                subtitle = "${snapshot.thermalStatusLabel} • ${snapshot.batteryVoltageMv} mV$cycleSuffix",
                progress = (snapshot.batteryPercent / 100f).coerceIn(0f, 1f),
                rag = thermalRag
            )
        }

        // 5. Live Network Throughput & Wi-Fi Link Quality
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = SentinelEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Network Throughput & Link",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        val vpnTag = if (snapshot.isVpnActive) " • VPN" else ""
                        Text(
                            text = "${snapshot.netTransportLabel}$vpnTag",
                            style = MaterialTheme.typography.labelSmall,
                            color = SentinelEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "↓ ${TelemetryFormatters.formatSpeedBytesPerSec(snapshot.netRxBytesPerSec)}   ↑ ${TelemetryFormatters.formatSpeedBytesPerSec(snapshot.netTxBytesPerSec)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (snapshot.wifiBandOrLinkLabel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = snapshot.wifiBandOrLinkLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 6. Internal Storage Summary
        item {
            TelemetryGaugeCard(
                title = "Internal Storage (/data)",
                icon = Icons.Default.Storage,
                headline = "${TelemetryFormatters.formatBytes(snapshot.freeStorageBytes)} Available",
                subtitle = "${(snapshot.storageUsageFraction * 100).toInt()}% used of ${TelemetryFormatters.formatBytes(snapshot.totalStorageBytes)}",
                progress = snapshot.storageUsageFraction,
                rag = storageRag
            )
        }

        // 7. Hardware, Kernel & Privileges Footer
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Device & Kernel Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${snapshot.deviceModel} • ${snapshot.socModel} (${snapshot.primaryAbi})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val shizukuNote = if (snapshot.isShizukuInstalled) " • Shizuku Detected" else ""
                    Text(
                        text = "${snapshot.androidVersionLabel} • Uptime ${TelemetryFormatters.formatUptime(snapshot.uptimeMillis)}$shizukuNote",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val deepSleepColor = if (snapshot.deepSleepPercent >= 55 || snapshot.isCharging || snapshot.uptimeMillis < 15 * 60_000L) {
                        SentinelEmerald
                    } else {
                        SentinelAmber
                    }
                    Text(
                        text = "Kernel Deep Sleep: ${snapshot.deepSleepPercent}% (${TelemetryFormatters.formatUptime(snapshot.deepSleepMillis)} suspended • ${TelemetryFormatters.formatUptime(snapshot.awakeMillis)} awake)",
                        style = MaterialTheme.typography.labelMedium,
                        color = deepSleepColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!snapshot.hasUsageStatsAccess) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Grant Usage Access to unlock per-app Cache Hogs & 24h Standby Buckets →",
                            style = MaterialTheme.typography.labelMedium,
                            color = SentinelEmerald,
                            modifier = Modifier.clickable { onOpenUsageAccess() }
                        )
                    }
                }
            }
        }
    }
}
