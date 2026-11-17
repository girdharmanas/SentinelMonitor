package com.sentinel.monitor.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sentinel.monitor.data.model.SentinelReleaseNotes
import com.sentinel.monitor.data.model.SentinelTab
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.preferences.MonitorAccentPreset
import com.sentinel.monitor.data.preferences.MonitorThemeMode
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.data.preferences.SentinelSettings
import com.sentinel.monitor.service.SentinelAutomationService
import com.sentinel.monitor.service.SentinelFirewallVpnService
import com.sentinel.monitor.service.SentinelWatchdogJobService
import com.sentinel.monitor.ui.components.SentinelMonitorCrestBadge
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelObsidian
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import com.sentinel.monitor.ui.theme.previewSwatchColor
import com.sentinel.monitor.util.StorageScavengerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelSettingsScreen(
    settings: SentinelSettings,
    snapshot: SystemSnapshot,
    onOpenReleaseNotes: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = SentinelPreferencesRepository.serializeBackupJson(settings)
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) "✓ Exported Sentinel Monitor settings backup" else "Export failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    Toast.makeText(context, "Selected backup file is empty", Toast.LENGTH_SHORT).show()
                } else {
                    val res = SentinelPreferencesRepository.importBackupJson(context, raw)
                    res.fold(
                        onSuccess = { imported ->
                            SentinelWatchdogJobService.scheduleOrCancel(context, imported.watchdogEnabled)
                            if (SentinelFirewallVpnService.isRunningFlow.value) {
                                SentinelFirewallVpnService.startOrReload(context)
                            }
                            Toast.makeText(context, "✓ Restored Sentinel Monitor settings", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Invalid backup JSON: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }

    val isNotifGranted = remember(snapshot.sampledAtMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    val isAutomationEnabled = remember(snapshot.sampledAtMillis) {
        SentinelAutomationService.isEnabled(context)
    }
    val hasScavengerAccess = remember(snapshot.sampledAtMillis) {
        StorageScavengerEngine.hasFullScavengerAccess()
    }

    Scaffold(
        containerColor = SentinelObsidian,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SentinelObsidian,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Settings & Themes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sentinel Monitor ${SentinelReleaseNotes.CURRENT_VERSION}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Theme Mode & Accent Palette Studio
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = SentinelEmerald)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Appearance & Theme Studio",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Canvas Theme Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        MonitorThemeMode.entries.chunked(2).forEach { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                pair.forEach { mode ->
                                    val selected = settings.themeMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(SentinelSurfaceHigh)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) SentinelEmerald else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                scope.launch {
                                                    SentinelPreferencesRepository.setThemeMode(context, mode)
                                                }
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) SentinelEmerald else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Accent Color Palette",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        MonitorAccentPreset.entries.chunked(2).forEach { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                pair.forEach { preset ->
                                    val selected = settings.accentPreset == preset
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SentinelSurfaceHigh)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) SentinelEmerald else Color.Transparent,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                scope.launch {
                                                    SentinelPreferencesRepository.setAccentPreset(context, preset)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(preset.previewSwatchColor())
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Telemetry Cadence, Units & Startup Preferences
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = SentinelCyan)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Telemetry, Units & Layout",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Live Telemetry Sampling Cadence",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                1000L to "1s Turbo",
                                2000L to "2s Balanced",
                                5000L to "5s Eco"
                            ).forEach { (ms, lbl) ->
                                FilterChip(
                                    selected = settings.pollingIntervalMs == ms,
                                    onClick = {
                                        scope.launch {
                                            SentinelPreferencesRepository.setPollingIntervalMs(context, ms)
                                        }
                                    },
                                    label = { Text(lbl) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text(
                            text = "Thermal Display Unit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !settings.useFahrenheit,
                                onClick = {
                                    scope.launch { SentinelPreferencesRepository.setUseFahrenheit(context, false) }
                                },
                                label = { Text("Celsius (°C)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = settings.useFahrenheit,
                                onClick = {
                                    scope.launch { SentinelPreferencesRepository.setUseFahrenheit(context, true) }
                                },
                                label = { Text("Fahrenheit (°F)") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Default Startup Tab",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SentinelTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = settings.defaultStartupTab == tab,
                                    onClick = {
                                        scope.launch {
                                            SentinelPreferencesRepository.setDefaultStartupTab(context, tab)
                                        }
                                    },
                                    label = { Text(tab.label) }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Compact Developer Process Rows",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Use tighter vertical spacing in Processes tab to fit more apps onscreen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.compactProcessRows,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        SentinelPreferencesRepository.setCompactProcessRows(context, enabled)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 3. Centralized Permissions & Privileges Hub
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SentinelEmerald)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Capabilities & Android Permissions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        PermissionStatusRow(
                            title = "Usage & Storage Stats Access",
                            subtitle = "Unlocks exact App Cache sizes, 24h Network usage & Standby Buckets",
                            isGranted = snapshot.hasUsageStatsAccess,
                            onAction = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                            }
                        )
                        PermissionStatusRow(
                            title = "1-Tap Force Stop & Cache Automation",
                            subtitle = "Automates batch Force Stop & Clear Cache in System Settings",
                            isGranted = isAutomationEnabled,
                            onAction = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            }
                        )
                        PermissionStatusRow(
                            title = "Floating Mini-HUD Overlay",
                            subtitle = "Allows draggable developer telemetry pill over games & apps",
                            isGranted = snapshot.hasOverlayPermission,
                            onAction = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                        )
                        PermissionStatusRow(
                            title = "All-Files Storage Scavenger",
                            subtitle = "Scans leftover APKs, ≥50 MB files & SHA-256 duplicates across storage",
                            isGranted = hasScavengerAccess,
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    }
                                }
                            }
                        )
                        PermissionStatusRow(
                            title = "Watchdog & Charge Limit Notifications",
                            subtitle = "Delivers thermal, low-storage & 80% battery ceiling alerts",
                            isGranted = isNotifGranted,
                            onAction = {
                                runCatching {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    } else {
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }

            // 4. Sentinel Suite Ecosystem Cross-Launcher
            item {
                val pm = context.packageManager
                val launcherIntent = remember { pm.getLaunchIntentForPackage("com.sentinel.launcher") }
                val readerIntent = remember { pm.getLaunchIntentForPackage("com.sentinel.reader") }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SentinelMonitorCrestBadge(modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Sentinel Suite Ecosystem",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (launcherIntent != null) {
                                        runCatching { context.startActivity(launcherIntent) }
                                    } else {
                                        Toast.makeText(context, "Sentinel Launcher is not installed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (launcherIntent != null) "Launcher" else "Launcher (N/A)", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (readerIntent != null) {
                                        runCatching { context.startActivity(readerIntent) }
                                    } else {
                                        Toast.makeText(context, "Sentinel Reader is not installed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (readerIntent != null) "Reader" else "Reader (N/A)", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 5. Backup, Restore & Data Maintenance
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Settings Backup, Restore & History",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Export or restore your Theme, Accent, Protected Whitelist (${settings.protectedPackages.size}), Firewall Rules (${settings.firewallBlockedPackages.size}) & Watchdog Thresholds as portable JSON.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("sentinel-monitor-settings.json") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export JSON")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import JSON")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        SentinelPreferencesRepository.clearVitalsHistory(context)
                                        Toast.makeText(context, "Cleared 24h Vitals history", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear 24h Ring (${settings.vitalsHistory.size})", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        SentinelPreferencesRepository.clearProtectedPackages(context)
                                        Toast.makeText(context, "Reset Protected Whitelist", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset Whitelist (${settings.protectedPackages.size})", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 6. About & Release Notes
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "About Sentinel Monitor",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Version ${SentinelReleaseNotes.CURRENT_VERSION} • Package ${context.packageName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onOpenReleaseNotes,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SentinelEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("View What's New & Release Notes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SentinelSurfaceHigh)
            .clickable { onAction() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SentinelEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = SentinelEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "Grant →",
                style = MaterialTheme.typography.labelSmall,
                color = SentinelAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
