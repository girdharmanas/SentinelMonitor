package com.sentinel.monitor.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.SensitivePermBadge
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.data.preferences.SentinelSettings
import com.sentinel.monitor.service.AutomationProgress
import com.sentinel.monitor.ui.components.AutomationBannerCard
import com.sentinel.monitor.ui.components.TelemetryGaugeCard
import com.sentinel.monitor.ui.components.TelemetrySparkline
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCrimson
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import kotlin.math.roundToInt

@Composable
fun StorageTab(
    snapshot: SystemSnapshot,
    processes: List<InstalledProcessEntry>,
    automationProgress: AutomationProgress,
    canUseSystemClearAllDialog: Boolean,
    onAutoClearTopCaches: () -> Unit,
    onSystemClearAllCaches: () -> Unit,
    onClearSingleAppCache: (InstalledProcessEntry) -> Unit,
    onCancelAutomation: () -> Unit,
    onDismissAutomationBanner: () -> Unit,
    onOpenAppStorage: (String) -> Unit,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rag = TelemetryRag.fromUsageFraction(snapshot.storageUsageFraction)
    val cacheHogs = remember(processes) {
        processes.filter { it.cacheBytes > 0L || it.totalStorageBytes > 0L }
            .sortedWith(
                compareByDescending<InstalledProcessEntry> { it.cacheBytes }
                    .thenByDescending { it.totalStorageBytes }
            )
            .take(40)
    }
    val totalReclaimableCache = remember(processes) {
        processes.sumOf { it.cacheBytes }
    }

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

        item {
            TelemetryGaugeCard(
                title = "User Data Partition (/data)",
                icon = Icons.Default.Storage,
                headline = "${TelemetryFormatters.formatBytes(snapshot.usedStorageBytes)} Used",
                subtitle = "${TelemetryFormatters.formatBytes(snapshot.freeStorageBytes)} free • Total ${TelemetryFormatters.formatBytes(snapshot.totalStorageBytes)}",
                progress = snapshot.storageUsageFraction,
                rag = rag
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = SentinelAmber)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Direct App Cache Cleaner",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (!snapshot.hasUsageStatsAccess) {
                        Text(
                            text = "Grant Usage Access so Sentinel can measure exact per-app Cache sizes and auto-clean top cache hogs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onGrantUsageAccess,
                                colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Grant Usage Access")
                            }
                            if (canUseSystemClearAllDialog) {
                                OutlinedButton(
                                    onClick = onSystemClearAllCaches,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("System Clear All")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "${TelemetryFormatters.formatBytes(totalReclaimableCache)} Reclaimable Cache Detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SentinelEmerald
                        )
                        Text(
                            text = "Clear top app caches automatically in 1 tap (user data & logins are strictly untouched).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onAutoClearTopCaches,
                                enabled = !automationProgress.isRunning,
                                colors = ButtonDefaults.buttonColors(containerColor = SentinelAmber),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Auto-Clear Cache Hogs")
                            }
                            if (canUseSystemClearAllDialog) {
                                OutlinedButton(
                                    onClick = onSystemClearAllCaches,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("OS Clear All")
                                }
                            }
                        }
                    }
                }
            }
        }

        items(cacheHogs, key = { it.packageName }) { app ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAppStorage(app.packageName) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Total ${TelemetryFormatters.formatBytes(app.totalStorageBytes)} • App ${TelemetryFormatters.formatBytes(app.appBytes)} • Data ${TelemetryFormatters.formatBytes(app.dataBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (app.cacheBytes > 0L) {
                            OutlinedButton(
                                onClick = { onClearSingleAppCache(app) },
                                enabled = !automationProgress.isRunning,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Clear ${TelemetryFormatters.formatBytes(app.cacheBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (app.cacheBytes >= 100L * 1024L * 1024L) SentinelAmber else SentinelEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = TelemetryFormatters.formatBytes(app.totalStorageBytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (app.totalStorageBytes > 0L && app.cacheBytes > 0L) {
                        Spacer(Modifier.height(6.dp))
                        val cacheFrac = (app.cacheBytes.toDouble() / app.totalStorageBytes.toDouble()).toFloat().coerceIn(0.04f, 1f)
                        LinearProgressIndicator(
                            progress = { cacheFrac },
                            color = SentinelAmber,
                            trackColor = SentinelSurfaceHigh,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                        )
                    }
                }
            }
        }
    }
}

private enum class SecurityFilter(val label: String) {
    ALL_SENSITIVE("Sensitive Access"),
    ACCESSIBILITY("Accessibility"),
    NOTIF_LISTENER("Notif Listener"),
    INSTALL_APKS("Install APKs"),
    OVERLAY("Draw Over Apps"),
    ALL_FILES("All Files"),
    SIDELOADED("Sideloaded"),
    LEGACY_SDK("Legacy SDK <31")
}

@Composable
fun SecurityTab(
    processes: List<InstalledProcessEntry>,
    onOpenAppDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by rememberSaveable { mutableStateOf(SecurityFilter.ALL_SENSITIVE) }

    val userApps = remember(processes) { processes.filter { !it.isSystemApp } }
    val accessibilityCount = remember(userApps) {
        userApps.count { SensitivePermBadge.ACCESSIBILITY in it.sensitivePermissions }
    }
    val notifListenerCount = remember(userApps) {
        userApps.count { SensitivePermBadge.NOTIFICATION_LISTENER in it.sensitivePermissions }
    }
    val sideloadCount = remember(userApps) { userApps.count { it.isSideloaded } }
    val legacySdkCount = remember(userApps) { userApps.count { it.isLegacyTargetSdk } }

    val matchingApps = remember(userApps, activeFilter) {
        when (activeFilter) {
            SecurityFilter.ALL_SENSITIVE -> userApps.filter { it.sensitivePermissions.isNotEmpty() }
            SecurityFilter.ACCESSIBILITY -> userApps.filter { SensitivePermBadge.ACCESSIBILITY in it.sensitivePermissions }
            SecurityFilter.NOTIF_LISTENER -> userApps.filter { SensitivePermBadge.NOTIFICATION_LISTENER in it.sensitivePermissions }
            SecurityFilter.INSTALL_APKS -> userApps.filter { SensitivePermBadge.INSTALL_PACKAGES in it.sensitivePermissions }
            SecurityFilter.OVERLAY -> userApps.filter { SensitivePermBadge.OVERLAY in it.sensitivePermissions }
            SecurityFilter.ALL_FILES -> userApps.filter { SensitivePermBadge.ALL_FILES in it.sensitivePermissions }
            SecurityFilter.SIDELOADED -> userApps.filter { it.isSideloaded }
            SecurityFilter.LEGACY_SDK -> userApps.filter { it.isLegacyTargetSdk }
        }.sortedByDescending { it.sensitivePermissions.size }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = SentinelEmerald)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Sentinel Permission & Sideload Audit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "$accessibilityCount Accessibility • $notifListenerCount Notification Listeners • $sideloadCount Sideloaded • $legacySdkCount Legacy SDK",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            SecurityFilter.entries.forEach { f ->
                FilterChip(
                    selected = activeFilter == f,
                    onClick = { activeFilter = f },
                    label = { Text(f.label) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(matchingApps, key = { it.packageName }) { app ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAppDetails(app.packageName) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.appLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${app.installerSourceLabel} • SDK ${app.targetSdk}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (app.isLegacyTargetSdk) SentinelCrimson else SentinelCyan
                            )
                        }
                        if (app.sensitivePermissions.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = app.sensitivePermissions.joinToString(" • ") { it.shortLabel },
                                style = MaterialTheme.typography.labelSmall,
                                color = SentinelAmber
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchdogTab(
    settings: SentinelSettings,
    isMiniHudRunning: Boolean,
    auditBanner: String?,
    onToggleWatchdog: (Boolean) -> Unit,
    onUpdateThresholds: (Float, Int, Float) -> Unit,
    onRunWatchdogNow: () -> Unit,
    onToggleMiniHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thermalSlider by remember(settings.thermalAlertThresholdC) {
        mutableFloatStateOf(settings.thermalAlertThresholdC)
    }
    var storageSlider by remember(settings.storageAlertFreePercent) {
        mutableFloatStateOf(settings.storageAlertFreePercent.toFloat())
    }
    var drainSlider by remember(settings.drainAlertPctPerHour) {
        mutableFloatStateOf(settings.drainAlertPctPerHour)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 24-Hour Vitals Sparkline History
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, contentDescription = null, tint = SentinelCyan)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "24-Hour Vitals Ring Buffer (${settings.vitalsHistory.size} pts)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (settings.vitalsHistory.size >= 2) {
                        Text(
                            text = "RAM Utilization Trend",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TelemetrySparkline(
                            values = settings.vitalsHistory.map { it.ramFraction },
                            color = SentinelEmerald
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Battery Temperature Trend (20°C–50°C)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TelemetrySparkline(
                            values = settings.vitalsHistory.map { it.tempCelsius },
                            minVal = 20f,
                            maxVal = 50f,
                            color = SentinelAmber
                        )
                    } else {
                        Text(
                            text = "Tap 'Sample & Check Watchdog Now' below (or keep Watchdog enabled) to populate your 24h trend curves.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Proactive Watchdog Thresholds
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SentinelEmerald)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Proactive Background Watchdog",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Zero-drain 15m JobScheduler alerts for heat, storage & battery spikes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.watchdogEnabled,
                            onCheckedChange = onToggleWatchdog
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Thermal Alert Limit: ${thermalSlider.roundToInt()}°C",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = thermalSlider,
                        onValueChange = { thermalSlider = it },
                        onValueChangeFinished = {
                            onUpdateThresholds(thermalSlider, storageSlider.roundToInt(), drainSlider)
                        },
                        valueRange = 37f..47f
                    )

                    Text(
                        text = "Low /data Storage Alert: ≤ ${storageSlider.roundToInt()}% Free",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = storageSlider,
                        onValueChange = { storageSlider = it },
                        onValueChangeFinished = {
                            onUpdateThresholds(thermalSlider, storageSlider.roundToInt(), drainSlider)
                        },
                        valueRange = 5f..20f
                    )

                    Text(
                        text = "Abnormal Discharge Alert: ≥ ${"%.1f".format(drainSlider)}%/hr",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = drainSlider,
                        onValueChange = { drainSlider = it },
                        onValueChangeFinished = {
                            onUpdateThresholds(thermalSlider, storageSlider.roundToInt(), drainSlider)
                        },
                        valueRange = 2.5f..10f
                    )

                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onRunWatchdogNow,
                        colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sample & Check Watchdog Now")
                    }
                    if (auditBanner != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = auditBanner,
                            style = MaterialTheme.typography.labelMedium,
                            color = SentinelCyan
                        )
                    }
                }
            }
        }

        // 3. Glanceable Surfaces (Mini-HUD, Quick Settings Tile & Home Widget)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, tint = SentinelCyan)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Glanceable Surfaces & Suite Integration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "• Floating Developer Mini-HUD: Draggable live pill (MHz • RAM • W • °C • Hz) over any app.\n• Quick Settings Tile: Edit your notification shade tiles to add 'Sentinel Sweep'.\n• Home Widget & Sentinel Launcher: Add 'Sentinel Vitals & Sweep' or tap telemetry cards on Sentinel Launcher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onToggleMiniHud,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isMiniHudRunning) "Stop Floating Mini-HUD" else "Launch Floating Mini-HUD")
                    }
                }
            }
        }
    }
}
