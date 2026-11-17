package com.sentinel.monitor.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.data.model.AppXRayDetails
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.ProcessExitSummary
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.service.AutomationProgress
import com.sentinel.monitor.ui.components.AutomationBannerCard
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCrimson
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import com.sentinel.monitor.util.AppInspectorEngine
import com.sentinel.monitor.util.StorageScavengerEngine
import kotlinx.coroutines.launch

private enum class ProcessFilterMode(val label: String) {
    RUNNING_NON_ESSENTIAL("Running Non-Essential"),
    CULPRITS_24H("24h Active"),
    USER_APPS("User Apps"),
    DOZE_VAMPIRES("Doze Exempt"),
    BOOT_STARTERS("Boot Auto-Start"),
    WHITELISTED("Protected"),
    SYSTEM("System"),
    EXIT_LOG("Exit / Crash Log")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessesTab(
    processes: List<InstalledProcessEntry>,
    nonEssentialRunning: List<InstalledProcessEntry>,
    exitSummaries: List<ProcessExitSummary>,
    protectedPackages: Set<String>,
    automationProgress: AutomationProgress,
    hasUsageAccess: Boolean,
    compactRows: Boolean = false,
    deepLinkInspectPackage: String? = null,
    onConsumeDeepLinkPackage: () -> Unit = {},
    onToggleProtected: (String) -> Unit,
    onStopAllNonEssential: () -> Unit,
    onForceStopSingleApp: (InstalledProcessEntry) -> Unit,
    onClearCacheSingleApp: (InstalledProcessEntry) -> Unit,
    onCancelAutomation: () -> Unit,
    onDismissAutomationBanner: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf(ProcessFilterMode.RUNNING_NON_ESSENTIAL) }
    var inspectedEntry by remember { mutableStateOf<InstalledProcessEntry?>(null) }
    var xrayDetails by remember { mutableStateOf<AppXRayDetails?>(null) }
    var isBackingUpApk by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkInspectPackage, processes) {
        val targetPkg = deepLinkInspectPackage ?: return@LaunchedEffect
        if (processes.isNotEmpty()) {
            val found = processes.firstOrNull { it.packageName == targetPkg }
            if (found != null) {
                inspectedEntry = found
            }
            onConsumeDeepLinkPackage()
        }
    }

    LaunchedEffect(inspectedEntry) {
        val target = inspectedEntry
        if (target == null) {
            xrayDetails = null
        } else {
            xrayDetails = AppInspectorEngine.inspectAppXRay(context, target)
        }
    }

    val filtered = remember(processes, nonEssentialRunning, query, filterMode, protectedPackages) {
        val base = when (filterMode) {
            ProcessFilterMode.RUNNING_NON_ESSENTIAL -> nonEssentialRunning
            ProcessFilterMode.CULPRITS_24H -> processes.filter { it.totalForegroundMillis24h > 0L || it.isRecentlyActive }
            ProcessFilterMode.USER_APPS -> processes.filter { !it.isSystemApp }
            ProcessFilterMode.DOZE_VAMPIRES -> processes.filter { !it.isSystemApp && it.isIgnoringBatteryOptimizations }
            ProcessFilterMode.BOOT_STARTERS -> processes.filter { !it.isSystemApp && it.isBootAutoStart }
            ProcessFilterMode.WHITELISTED -> processes.filter { it.packageName in protectedPackages }
            ProcessFilterMode.SYSTEM -> processes.filter { it.isSystemApp }
            ProcessFilterMode.EXIT_LOG -> emptyList()
        }
        if (query.isBlank()) base else base.filter {
            it.appLabel.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (automationProgress.isRunning || automationProgress.lastSummaryBanner != null) {
            Spacer(Modifier.height(6.dp))
            AutomationBannerCard(
                progress = automationProgress,
                onCancel = onCancelAutomation,
                onDismissSummary = onDismissAutomationBanner
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search apps, packages, trackers, or culprits…") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            ProcessFilterMode.entries.forEach { mode ->
                val countBadge = when (mode) {
                    ProcessFilterMode.RUNNING_NON_ESSENTIAL -> " (${nonEssentialRunning.size})"
                    ProcessFilterMode.DOZE_VAMPIRES -> {
                        val n = processes.count { !it.isSystemApp && it.isIgnoringBatteryOptimizations }
                        if (n > 0) " ($n)" else ""
                    }
                    else -> ""
                }
                FilterChip(
                    selected = filterMode == mode,
                    onClick = { filterMode = mode },
                    label = { Text("${mode.label}$countBadge") }
                )
            }
        }

        if (filterMode != ProcessFilterMode.EXIT_LOG) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${nonEssentialRunning.size} Non-Essential Apps Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap any row for Tracker SDK X-Ray & 1-tap APK Backup.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onStopAllNonEssential,
                        enabled = !automationProgress.isRunning && nonEssentialRunning.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop All")
                    }
                }
            }
        }

        if (!hasUsageAccess && filterMode != ProcessFilterMode.EXIT_LOG) {
            Text(
                text = "Enable Usage Access for 24h CPU foreground, Network Data, Standby Buckets & Cache sizes →",
                style = MaterialTheme.typography.labelSmall,
                color = SentinelEmerald,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable { onGrantUsageAccess() }
            )
        }

        if (filterMode == ProcessFilterMode.EXIT_LOG) {
            ExitReasonsList(exitSummaries = exitSummaries, query = query)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.packageName }) { item ->
                    val isProtected = item.packageName in protectedPackages
                    val rowVertPad = if (compactRows) 6.dp else 12.dp
                    Card(
                        shape = RoundedCornerShape(if (compactRows) 14.dp else 18.dp),
                        colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { inspectedEntry = item }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = rowVertPad, bottom = rowVertPad)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.appLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    StandbyBadge(if (item.isStopped) "STOPPED" else item.standbyBucketLabel)
                                }
                                Text(
                                    text = item.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val fgMin = item.totalForegroundMillis24h / 60_000L
                                val metaParts = buildList {
                                    if (fgMin > 0L) add("${fgMin}m active")
                                    if (item.totalStorageBytes > 0L) {
                                        add(TelemetryFormatters.formatBytes(item.totalStorageBytes))
                                    }
                                    if (item.totalNetworkBytes24h >= 64 * 1024L) {
                                        add("Net ${TelemetryFormatters.formatBytes(item.totalNetworkBytes24h)}")
                                    }
                                    if (item.isIgnoringBatteryOptimizations) add("Doze Exempt")
                                    if (item.isBootAutoStart && !item.isSystemApp) add("Boot")
                                    add("SDK ${item.targetSdk}")
                                }
                                Text(
                                    text = metaParts.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.isIgnoringBatteryOptimizations) SentinelAmber else SentinelEmerald
                                )
                            }
                            if (!item.isStopped && !item.isSystemApp && item.packageName != context.packageName) {
                                IconButton(onClick = { onForceStopSingleApp(item) }) {
                                    Icon(
                                        imageVector = Icons.Default.StopCircle,
                                        contentDescription = "Force Stop App",
                                        tint = SentinelCrimson
                                    )
                                }
                            }
                            IconButton(onClick = { onToggleProtected(item.packageName) }) {
                                Icon(
                                    imageVector = if (isProtected) Icons.Filled.Shield else Icons.Outlined.Shield,
                                    contentDescription = "Toggle Protected Whitelist",
                                    tint = if (isProtected) SentinelEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (inspectedEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { inspectedEntry = null },
            containerColor = SentinelSurface
        ) {
            val target = inspectedEntry!!
            AppXRayBottomSheetContent(
                entry = target,
                xray = xrayDetails,
                isProtected = target.packageName in protectedPackages,
                isBackingUpApk = isBackingUpApk,
                onToggleProtected = { onToggleProtected(target.packageName) },
                onBackupApk = {
                    if (!isBackingUpApk) {
                        isBackingUpApk = true
                        scope.launch {
                            val res = StorageScavengerEngine.exportInstalledApkToDownloads(context, target)
                            isBackingUpApk = false
                            val msg = res.fold(
                                onSuccess = { path -> "✓ Backed up APK to $path" },
                                onFailure = { err -> "APK backup failed: ${err.message}" }
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onForceStop = {
                    inspectedEntry = null
                    onForceStopSingleApp(target)
                },
                onClearCache = {
                    inspectedEntry = null
                    onClearCacheSingleApp(target)
                },
                onOpenAppInfo = {
                    val pkg = target.packageName
                    inspectedEntry = null
                    onOpenAppDetails(pkg)
                },
                onUninstall = {
                    val pkg = target.packageName
                    inspectedEntry = null
                    onUninstallApp(pkg)
                }
            )
        }
    }
}

@Composable
private fun StandbyBadge(bucket: String) {
    val color = when (bucket) {
        "ACTIVE" -> SentinelAmber
        "WORKING_SET" -> SentinelCyan
        "STOPPED" -> Color(0xFF64748B)
        else -> SentinelEmerald
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = bucket,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ExitReasonsList(exitSummaries: List<ProcessExitSummary>, query: String) {
    val filtered = remember(exitSummaries, query) {
        if (query.isBlank()) exitSummaries else exitSummaries.filter {
            it.processName.contains(query, ignoreCase = true) ||
                it.reasonLabel.contains(query, ignoreCase = true)
        }
    }
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No recent kernel OOM, ANR, or crash exit records reported by ActivityManager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(filtered) { ex ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ex.processName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ex.reasonLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ex.reasonLabel.contains("CRASH") || ex.reasonLabel.contains("ANR") || ex.reasonLabel.contains("OOM")) SentinelCrimson else SentinelCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = ex.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ex.pssKb > 0L) {
                        Text(
                            text = "Peak PSS Memory: ${TelemetryFormatters.formatBytes(ex.pssKb * 1024L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SentinelEmerald
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppXRayBottomSheetContent(
    entry: InstalledProcessEntry,
    xray: AppXRayDetails?,
    isProtected: Boolean,
    isBackingUpApk: Boolean,
    onToggleProtected: () -> Unit,
    onBackupApk: () -> Unit,
    onForceStop: () -> Unit,
    onClearCache: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = entry.appLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${entry.packageName} • v${entry.versionName.ifBlank { "1.0" }} (${xray?.versionCode ?: ""})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SentinelSurfaceHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Binary, Storage & Power X-Ray",
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinelCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SDK: minSdk ${entry.minSdk} → targetSdk ${entry.targetSdk} • Source: ${entry.installerSourceLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Native ABIs: ${xray?.nativeAbis?.joinToString(", ") ?: "Inspecting…"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Footprint: App ${TelemetryFormatters.formatBytes(entry.appBytes)} • Data ${TelemetryFormatters.formatBytes(entry.dataBytes)} • Cache ${TelemetryFormatters.formatBytes(entry.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SentinelEmerald
                )
                Text(
                    text = "24h Data: Wi-Fi ${TelemetryFormatters.formatBytes(entry.wifiDataBytes24h)} • Cellular ${TelemetryFormatters.formatBytes(entry.mobileDataBytes24h)} • Doze: ${if (entry.isIgnoringBatteryOptimizations) "EXEMPT (Unrestricted)" else "Optimized"} • Boot: ${if (entry.isBootAutoStart) "Auto-Start" else "None"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.isIgnoringBatteryOptimizations) SentinelAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Embedded Ad & Tracker SDK Scanner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SentinelSurfaceHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val trackers = xray?.detectedTrackers
                Text(
                    text = "Embedded Tracker & Ad-SDK Scanner",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (trackers.isNullOrEmpty()) SentinelEmerald else SentinelAmber,
                    fontWeight = FontWeight.Bold
                )
                when {
                    trackers == null -> {
                        Text(
                            text = "Scanning manifest activities, services, receivers & providers…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    trackers.isEmpty() -> {
                        Text(
                            text = "✓ Zero known Ad / Analytics / Attribution SDK signatures detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = SentinelEmerald
                        )
                    }
                    else -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            trackers.forEach { sdk ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SentinelAmber.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = sdk,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SentinelAmber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Direct 1-Tap Force Stop & Clear Cache Row
        if (entry.packageName != context.packageName) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onForceStop,
                    enabled = !entry.isStopped,
                    colors = ButtonDefaults.buttonColors(containerColor = SentinelCrimson),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (entry.isStopped) "Already Stopped" else "Force Stop")
                }
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(containerColor = SentinelAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Cache")
                }
            }
        }

        if (entry.sensitivePermissions.isNotEmpty()) {
            Text(
                text = "Granted High-Privilege Capabilities",
                style = MaterialTheme.typography.labelLarge,
                color = SentinelAmber
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entry.sensitivePermissions.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SentinelAmber.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badge.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = SentinelAmber
                        )
                    }
                }
            }
        }

        if (xray != null) {
            FingerprintCopyRow(context = context, label = "SHA-1", value = xray.sha1Fingerprint)
            FingerprintCopyRow(context = context, label = "SHA-256", value = xray.sha256Fingerprint)
        }

        // APK Backup Vault Button
        OutlinedButton(
            onClick = onBackupApk,
            enabled = !isBackingUpApk,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = SentinelCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isBackingUpApk) "Extracting APK…" else "Backup Base APK to Downloads/Sentinel Backups")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 18.dp)
        ) {
            OutlinedButton(
                onClick = onToggleProtected,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isProtected) "Protected" else "Protect")
            }
            OutlinedButton(
                onClick = onOpenAppInfo,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("App Info")
            }
            if (!entry.isSystemApp && entry.packageName != context.packageName) {
                OutlinedButton(
                    onClick = onUninstall,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Uninstall",
                        tint = SentinelCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerprintCopyRow(context: Context, label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SentinelSurfaceHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Sentinel $label", value))
                Toast
                    .makeText(context, "Copied $label fingerprint", Toast.LENGTH_SHORT)
                    .show()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$label Signing Certificate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = SentinelEmerald,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
