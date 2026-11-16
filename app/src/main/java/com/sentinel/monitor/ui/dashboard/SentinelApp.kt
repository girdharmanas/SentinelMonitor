package com.sentinel.monitor.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.ProcessExitSummary
import com.sentinel.monitor.data.model.SentinelTab
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.VitalsHistoryPoint
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.data.preferences.SentinelSettings
import com.sentinel.monitor.service.MiniHudOverlayService
import com.sentinel.monitor.service.SentinelWatchdogJobService
import com.sentinel.monitor.ui.components.SentinelMonitorCrestBadge
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelObsidian
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.util.AppInspectorEngine
import com.sentinel.monitor.util.SystemTelemetryEngine
import com.sentinel.monitor.widget.SentinelGlanceWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelApp(deepLinkTabFlow: StateFlow<SentinelTab?>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by SentinelPreferencesRepository.settingsFlow(context)
        .collectAsState(initial = SentinelSettings())
    val isMiniHudRunning by MiniHudOverlayService.isRunningFlow.collectAsState()
    val requestedTab by deepLinkTabFlow.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(SentinelTab.VITALS) }
    var isLivePolling by rememberSaveable { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf(SystemSnapshot()) }
    var processes by remember { mutableStateOf<List<InstalledProcessEntry>>(emptyList()) }
    var exitSummaries by remember { mutableStateOf<List<ProcessExitSummary>>(emptyList()) }
    var isSweeping by remember { mutableStateOf(false) }
    var sweepBanner by remember { mutableStateOf<String?>(null) }
    var watchdogBanner by remember { mutableStateOf<String?>(null) }

    val liveRamSparkline = remember { mutableStateListOf<Float>() }
    val liveCpuSparkline = remember { mutableStateListOf<Float>() }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab!!
        }
    }

    fun recordLiveSample(snap: SystemSnapshot) {
        snapshot = snap
        liveRamSparkline.add(snap.ramUsageFraction)
        if (liveRamSparkline.size > 60) liveRamSparkline.removeAt(0)
        liveCpuSparkline.add(snap.avgCpuUtilizationFraction)
        if (liveCpuSparkline.size > 60) liveCpuSparkline.removeAt(0)
    }

    // Lifecycle-gated zero-overhead polling when backgrounded
    LaunchedEffect(lifecycleOwner, isLivePolling) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val initialSnap = SystemTelemetryEngine.sampleSnapshot(context)
            recordLiveSample(initialSnap)
            SentinelPreferencesRepository.appendVitalsHistoryPoint(
                context,
                VitalsHistoryPoint(
                    timestampMs = initialSnap.sampledAtMillis,
                    ramFraction = initialSnap.ramUsageFraction,
                    batteryPercent = initialSnap.batteryPercent,
                    tempCelsius = initialSnap.batteryTempCelsius,
                    powerWatts = if (initialSnap.isCharging) initialSnap.batteryPowerWatts else -initialSnap.batteryPowerWatts,
                    cpuFraction = initialSnap.avgCpuUtilizationFraction
                )
            )
            processes = SystemTelemetryEngine.queryProcessCatalog(context)
            exitSummaries = AppInspectorEngine.queryRecentExitReasons(context)

            while (isLivePolling) {
                delay(2000L)
                recordLiveSample(SystemTelemetryEngine.sampleSnapshot(context))
            }
        }
    }

    val openAppDetails: (String) -> Unit = { pkg ->
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", pkg, null)
                }
            )
        }
    }

    val openUsageAccess: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    val toggleMiniHud: () -> Unit = {
        if (isMiniHudRunning) {
            MiniHudOverlayService.stop(context)
        } else if (Settings.canDrawOverlays(context)) {
            MiniHudOverlayService.start(context)
        } else {
            Toast.makeText(
                context,
                "Grant 'Display over other apps' to enable Sentinel Mini-HUD",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    Scaffold(
        containerColor = SentinelObsidian,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SentinelObsidian,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SentinelMonitorCrestBadge(modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sentinel Monitor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isLivePolling) "Live Telemetry • 2s cadence" else "Telemetry Paused • 0-FPS Idle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isLivePolling = !isLivePolling }) {
                        Icon(
                            imageVector = if (isLivePolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLivePolling) "Pause live polling" else "Resume live polling",
                            tint = SentinelEmerald
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SentinelSurface) {
                NavigationBarItem(
                    selected = selectedTab == SentinelTab.VITALS,
                    onClick = { selectedTab = SentinelTab.VITALS },
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                    label = { Text("Vitals") }
                )
                NavigationBarItem(
                    selected = selectedTab == SentinelTab.PROCESSES,
                    onClick = {
                        selectedTab = SentinelTab.PROCESSES
                        scope.launch {
                            processes = SystemTelemetryEngine.queryProcessCatalog(context)
                            exitSummaries = AppInspectorEngine.queryRecentExitReasons(context)
                        }
                    },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    label = { Text("Processes") }
                )
                NavigationBarItem(
                    selected = selectedTab == SentinelTab.STORAGE,
                    onClick = {
                        selectedTab = SentinelTab.STORAGE
                        if (processes.isEmpty()) {
                            scope.launch { processes = SystemTelemetryEngine.queryProcessCatalog(context) }
                        }
                    },
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text("Storage") }
                )
                NavigationBarItem(
                    selected = selectedTab == SentinelTab.SECURITY,
                    onClick = { selectedTab = SentinelTab.SECURITY },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    label = { Text("Security") }
                )
                NavigationBarItem(
                    selected = selectedTab == SentinelTab.WATCHDOG,
                    onClick = { selectedTab = SentinelTab.WATCHDOG },
                    icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                    label = { Text("Watchdog") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            SentinelTab.VITALS -> VitalsTab(
                snapshot = snapshot,
                liveRamSparkline = liveRamSparkline,
                liveCpuSparkline = liveCpuSparkline,
                isSweeping = isSweeping,
                sweepBanner = sweepBanner,
                isMiniHudRunning = isMiniHudRunning,
                protectedCount = settings.protectedPackages.size,
                onRunMemorySweep = {
                    if (!isSweeping) {
                        isSweeping = true
                        scope.launch {
                            val report = SystemTelemetryEngine.performSafeMemorySweep(context)
                            recordLiveSample(SystemTelemetryEngine.sampleSnapshot(context))
                            SentinelGlanceWidgetProvider.refreshAllWidgets(context)
                            sweepBanner = report.summaryLabel()
                            isSweeping = false
                        }
                    }
                },
                onToggleMiniHud = toggleMiniHud,
                onOpenUsageAccess = openUsageAccess,
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.PROCESSES -> ProcessesTab(
                processes = processes,
                exitSummaries = exitSummaries,
                protectedPackages = settings.protectedPackages,
                hasUsageAccess = snapshot.hasUsageStatsAccess,
                onToggleProtected = { pkg ->
                    scope.launch { SentinelPreferencesRepository.toggleProtectedPackage(context, pkg) }
                },
                onOpenAppDetails = openAppDetails,
                onUninstallApp = { pkg ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
                        )
                    }
                },
                onGrantUsageAccess = openUsageAccess,
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.STORAGE -> StorageTab(
                snapshot = snapshot,
                processes = processes,
                onOpenAppStorage = openAppDetails,
                onGrantUsageAccess = openUsageAccess,
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.SECURITY -> SecurityTab(
                processes = processes,
                onOpenAppDetails = openAppDetails,
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.WATCHDOG -> WatchdogTab(
                settings = settings,
                isMiniHudRunning = isMiniHudRunning,
                auditBanner = watchdogBanner,
                onToggleWatchdog = { enabled ->
                    scope.launch {
                        SentinelPreferencesRepository.setWatchdogEnabled(context, enabled)
                        SentinelWatchdogJobService.scheduleOrCancel(context, enabled)
                    }
                },
                onUpdateThresholds = { therm, stor, drain ->
                    scope.launch {
                        SentinelPreferencesRepository.updateWatchdogThresholds(context, therm, stor, drain)
                    }
                },
                onRunWatchdogNow = {
                    scope.launch {
                        val alerts = SentinelWatchdogJobService.runAuditNow(context, forceSavePoint = true)
                        watchdogBanner = if (alerts.isEmpty()) {
                            "✓ Vitals recorded • All thresholds nominal"
                        } else {
                            "⚠ ${alerts.joinToString(" | ")}"
                        }
                    }
                },
                onToggleMiniHud = toggleMiniHud,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
