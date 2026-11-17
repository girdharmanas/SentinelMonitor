package com.sentinel.monitor.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.ScavengerFileItem
import com.sentinel.monitor.data.model.SensitivePermBadge
import com.sentinel.monitor.data.model.SensorBlackboxEvent
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.service.AutomationProgress
import com.sentinel.monitor.service.SentinelFirewallVpnService
import com.sentinel.monitor.ui.components.AutomationBannerCard
import com.sentinel.monitor.ui.components.TelemetryGaugeCard
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCrimson
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import com.sentinel.monitor.util.StorageScavengerEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StorageSubMode(val label: String) {
    APP_CACHES("App Caches"),
    SCAVENGER("Storage Scavenger")
}

private enum class ScavengerFilter(val label: String) {
    ALL("All"),
    INSTALLER("Leftover APKs"),
    LARGE_FILE("Large ≥50MB"),
    DUPLICATE("SHA-256 Duplicates")
}

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subMode by rememberSaveable { mutableStateOf(StorageSubMode.APP_CACHES) }
    var scavengerFilter by rememberSaveable { mutableStateOf(ScavengerFilter.ALL) }
    var scavengerItems by remember { mutableStateOf<List<ScavengerFileItem>>(emptyList()) }
    var isScanningScavenger by remember { mutableStateOf(false) }
    var hasScannedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(subMode) {
        if (subMode == StorageSubMode.SCAVENGER && !hasScannedOnce && !isScanningScavenger) {
            isScanningScavenger = true
            scavengerItems = StorageScavengerEngine.scanStorageScavenger(context)
            hasScannedOnce = true
            isScanningScavenger = false
        }
    }

    val rag = TelemetryRag.fromUsageFraction(snapshot.storageUsageFraction)
    val cacheHogs = remember(processes) {
        processes.filter { it.cacheBytes > 0L || it.totalStorageBytes > 0L }
            .sortedWith(
                compareByDescending<InstalledProcessEntry> { it.cacheBytes }
                    .thenByDescending { it.totalStorageBytes }
            )
            .take(40)
    }
    val totalReclaimableCache = remember(processes) { processes.sumOf { it.cacheBytes } }
    val filteredScavenger = remember(scavengerItems, scavengerFilter) {
        if (scavengerFilter == ScavengerFilter.ALL) {
            scavengerItems
        } else {
            scavengerItems.filter { it.category == scavengerFilter.name }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            StorageSubMode.entries.forEach { mode ->
                FilterChip(
                    selected = subMode == mode,
                    onClick = { subMode = mode },
                    label = { Text(mode.label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (subMode == StorageSubMode.APP_CACHES) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
        } else {
            val hasFullAccess = StorageScavengerEngine.hasFullScavengerAccess()
            val totalScavengerBytes = remember(filteredScavenger) { filteredScavenger.sumOf { it.sizeBytes } }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderDelete, contentDescription = null, tint = SentinelCyan)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Deep Storage Scavenger",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${filteredScavenger.size} items (${TelemetryFormatters.formatBytes(totalScavengerBytes)}) across Downloads & Media",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (!isScanningScavenger) {
                                            isScanningScavenger = true
                                            scope.launch {
                                                scavengerItems = StorageScavengerEngine.scanStorageScavenger(context)
                                                hasScannedOnce = true
                                                isScanningScavenger = false
                                            }
                                        }
                                    },
                                    enabled = !isScanningScavenger,
                                    colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isScanningScavenger) "Scanning SHA-256…" else "Rescan Storage")
                                }
                                if (!hasFullAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Grant Full Scan")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        ScavengerFilter.entries.forEach { f ->
                            val count = if (f == ScavengerFilter.ALL) {
                                scavengerItems.size
                            } else {
                                scavengerItems.count { it.category == f.name }
                            }
                            FilterChip(
                                selected = scavengerFilter == f,
                                onClick = { scavengerFilter = f },
                                label = { Text("${f.label} ($count)") }
                            )
                        }
                    }
                }

                if (filteredScavenger.isEmpty() && !isScanningScavenger) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ Clean! No leftover APK installers, ≥50 MB space hogs, or exact SHA-256 duplicates found in accessible folders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SentinelEmerald,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                items(filteredScavenger, key = { "${it.category}:${it.absolutePath}" }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.category} • ${TelemetryFormatters.formatBytes(item.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (item.category) {
                                        "DUPLICATE" -> SentinelAmber
                                        "INSTALLER" -> SentinelEmerald
                                        else -> SentinelCyan
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val ok = StorageScavengerEngine.deleteScavengerFile(item.absolutePath)
                                        if (ok) {
                                            scavengerItems = scavengerItems.filterNot { it.absolutePath == item.absolutePath }
                                            Toast.makeText(
                                                context,
                                                "Deleted ${item.displayName} (${TelemetryFormatters.formatBytes(item.sizeBytes)} freed)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Grant 'Full Scan' access to delete files outside Sentinel's sandbox",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete File",
                                    tint = SentinelCrimson
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SecuritySubMode(val label: String) {
    PERMISSIONS("Audit"),
    FIREWALL("App Firewall"),
    BLACKBOX("Sensor Log")
}

private enum class SecurityFilter(val label: String) {
    ALL_SENSITIVE("Sensitive Access"),
    DOZE_VAMPIRES("Doze Exempt"),
    BOOT_STARTERS("Boot Auto-Start"),
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
    firewallBlockedPackages: Set<String>,
    sensorBlackboxLog: List<SensorBlackboxEvent>,
    onToggleFirewallPackage: (String) -> Unit,
    onClearSensorBlackbox: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var subMode by rememberSaveable { mutableStateOf(SecuritySubMode.PERMISSIONS) }
    var activeFilter by rememberSaveable { mutableStateOf(SecurityFilter.ALL_SENSITIVE) }
    var firewallQuery by rememberSaveable { mutableStateOf("") }

    val isFirewallRunning by SentinelFirewallVpnService.isRunningFlow.collectAsState()
    val activeBlockedCount by SentinelFirewallVpnService.activeBlockedCountFlow.collectAsState()

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            SentinelFirewallVpnService.startOrReload(context)
        } else {
            Toast.makeText(context, "VPN permission is required for local sinkhole firewall", Toast.LENGTH_SHORT).show()
        }
    }

    val startOrPromptFirewall: () -> Unit = {
        val prep = runCatching { VpnService.prepare(context) }.getOrNull()
        if (prep != null) {
            vpnConsentLauncher.launch(prep)
        } else {
            SentinelFirewallVpnService.startOrReload(context)
        }
    }

    val userApps = remember(processes) { processes.filter { !it.isSystemApp } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            SecuritySubMode.entries.forEach { m ->
                FilterChip(
                    selected = subMode == m,
                    onClick = { subMode = m },
                    label = { Text(m.label, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (subMode) {
            SecuritySubMode.PERMISSIONS -> {
                val accessibilityCount = remember(userApps) {
                    userApps.count { SensitivePermBadge.ACCESSIBILITY in it.sensitivePermissions }
                }
                val dozeCount = remember(userApps) { userApps.count { it.isIgnoringBatteryOptimizations } }
                val sideloadCount = remember(userApps) { userApps.count { it.isSideloaded } }
                val legacySdkCount = remember(userApps) { userApps.count { it.isLegacyTargetSdk } }

                val matchingApps = remember(userApps, activeFilter) {
                    when (activeFilter) {
                        SecurityFilter.ALL_SENSITIVE -> userApps.filter { it.sensitivePermissions.isNotEmpty() }
                        SecurityFilter.DOZE_VAMPIRES -> userApps.filter { it.isIgnoringBatteryOptimizations }
                        SecurityFilter.BOOT_STARTERS -> userApps.filter { it.isBootAutoStart }
                        SecurityFilter.ACCESSIBILITY -> userApps.filter { SensitivePermBadge.ACCESSIBILITY in it.sensitivePermissions }
                        SecurityFilter.NOTIF_LISTENER -> userApps.filter { SensitivePermBadge.NOTIFICATION_LISTENER in it.sensitivePermissions }
                        SecurityFilter.INSTALL_APKS -> userApps.filter { SensitivePermBadge.INSTALL_PACKAGES in it.sensitivePermissions }
                        SecurityFilter.OVERLAY -> userApps.filter { SensitivePermBadge.OVERLAY in it.sensitivePermissions }
                        SecurityFilter.ALL_FILES -> userApps.filter { SensitivePermBadge.ALL_FILES in it.sensitivePermissions }
                        SecurityFilter.SIDELOADED -> userApps.filter { it.isSideloaded }
                        SecurityFilter.LEGACY_SDK -> userApps.filter { it.isLegacyTargetSdk }
                    }.sortedByDescending { it.sensitivePermissions.size }
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SentinelEmerald)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Permission, Doze & Sideload Audit",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "$accessibilityCount Accessibility • $dozeCount Doze-Exempt • $sideloadCount Sideloaded • $legacySdkCount Legacy SDK",
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
                                val tags = buildList {
                                    app.sensitivePermissions.forEach { add(it.shortLabel) }
                                    if (app.isIgnoringBatteryOptimizations) add("Doze Exempt")
                                    if (app.isBootAutoStart) add("Boot Receiver")
                                }
                                if (tags.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = tags.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SentinelAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SecuritySubMode.FIREWALL -> {
                val firewallCandidates = remember(userApps, firewallBlockedPackages, firewallQuery) {
                    val base = userApps
                        .filter { it.packageName != context.packageName }
                        .sortedWith(
                            compareByDescending<InstalledProcessEntry> { it.packageName in firewallBlockedPackages }
                                .thenByDescending { it.totalNetworkBytes24h }
                                .thenBy { it.appLabel.lowercase(Locale.US) }
                        )
                    if (firewallQuery.isBlank()) base else base.filter {
                        it.appLabel.contains(firewallQuery, ignoreCase = true) ||
                            it.packageName.contains(firewallQuery, ignoreCase = true)
                    }
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isFirewallRunning) SentinelEmerald else SentinelCyan
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Zero-Root Sinkhole App Firewall",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isFirewallRunning) {
                                        "Active • Blocking internet for $activeBlockedCount apps (0% CPU kernel route)"
                                    } else {
                                        "${firewallBlockedPackages.size} apps selected • Unblocked apps bypass VPN 100%"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isFirewallRunning,
                                onCheckedChange = { turnOn ->
                                    if (turnOn) {
                                        if (firewallBlockedPackages.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                "Toggle at least 1 app below to block its internet access",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            startOrPromptFirewall()
                                        }
                                    } else {
                                        SentinelFirewallVpnService.stop(context)
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = firewallQuery,
                    onValueChange = { firewallQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Filter apps to block Wi-Fi & Cellular…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(firewallCandidates, key = { it.packageName }) { app ->
                        val isBlocked = app.packageName in firewallBlockedPackages
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "24h Data: Wi-Fi ${TelemetryFormatters.formatBytes(app.wifiDataBytes24h)} • Cell ${TelemetryFormatters.formatBytes(app.mobileDataBytes24h)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isBlocked) SentinelCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isBlocked,
                                    onCheckedChange = {
                                        onToggleFirewallPackage(app.packageName)
                                        if (isFirewallRunning) {
                                            SentinelFirewallVpnService.startOrReload(context)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SecuritySubMode.BLACKBOX -> {
                val reversedEvents = remember(sensorBlackboxLog) { sensorBlackboxLog.asReversed() }
                val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = SentinelAmber)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Camera & Microphone Privacy Blackbox",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Event-driven OS hardware session timeline (${reversedEvents.size} events recorded)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (reversedEvents.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onClearSensorBlackbox,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }

                if (reversedEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No Camera or Microphone activations recorded yet while Sentinel is installed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reversedEvents) { ev ->
                            val isCam = ev.sensorType == "CAMERA"
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background((if (isCam) SentinelAmber else SentinelCyan).copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isCam) Icons.Default.PhotoCamera else Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = if (isCam) SentinelAmber else SentinelCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isCam) "Camera Hardware Session" else "Microphone Recording Session",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = fmt.format(Date(ev.timestampMs)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val sec = (ev.durationMillis / 1000.0)
                                    Text(
                                        text = String.format(Locale.US, "%.1fs", sec),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SentinelEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
