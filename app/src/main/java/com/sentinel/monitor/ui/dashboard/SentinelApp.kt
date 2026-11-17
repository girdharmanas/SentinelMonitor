package com.sentinel.monitor.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import com.sentinel.monitor.data.model.SentinelDeepLinkRequest
import com.sentinel.monitor.data.model.SentinelReleaseNotes
import com.sentinel.monitor.data.model.SentinelTab
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.VitalsHistoryPoint
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.data.preferences.SentinelSettings
import com.sentinel.monitor.service.MiniHudOverlayService
import com.sentinel.monitor.service.SentinelAutomationService
import com.sentinel.monitor.service.SentinelWatchdogJobService
import com.sentinel.monitor.ui.components.AutomationSetupBottomSheet
import com.sentinel.monitor.ui.components.ReleaseNotesBottomSheet
import com.sentinel.monitor.ui.components.SentinelMonitorCrestBadge
import com.sentinel.monitor.ui.theme.SentinelCyan
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
fun SentinelApp(deepLinkRequestFlow: StateFlow<SentinelDeepLinkRequest?>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by SentinelPreferencesRepository.settingsFlow(context)
        .collectAsState(initial = SentinelSettings())
    val isMiniHudRunning by MiniHudOverlayService.isRunningFlow.collectAsState()
    val automationProgress by SentinelAutomationService.progressFlow.collectAsState()
    val deepLinkReq by deepLinkRequestFlow.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(SentinelTab.VITALS) }
    var appliedInitialTab by rememberSaveable { mutableStateOf(false) }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var pendingInspectPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLivePolling by rememberSaveable { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf(SystemSnapshot()) }
    var processes by remember { mutableStateOf<List<InstalledProcessEntry>>(emptyList()) }
    var exitSummaries by remember { mutableStateOf<List<ProcessExitSummary>>(emptyList()) }
    var isSweeping by remember { mutableStateOf(false) }
    var sweepBanner by remember { mutableStateOf<String?>(null) }
    var watchdogBanner by remember { mutableStateOf<String?>(null) }

    var pendingSetupPromptTitle by remember { mutableStateOf<String?>(null) }
    var pendingSetupAllowCacheDialog by remember { mutableStateOf(false) }
    var showReleaseNotesSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.isLoaded, settings.lastSeenReleaseVersion, deepLinkReq) {
        if (settings.isLoaded &&
            settings.lastSeenReleaseVersion != SentinelReleaseNotes.CURRENT_VERSION &&
            deepLinkReq == null
        ) {
            showReleaseNotesSheet = true
        }
    }

    val canUseSystemClearAllDialog = remember(context) {
        AppInspectorEngine.canUseSystemClearAllCacheDialog(context)
    }

    val systemClearCacheLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch {
            snapshot = SystemTelemetryEngine.sampleSnapshot(context)
            processes = SystemTelemetryEngine.queryProcessCatalog(context)
        }
    }

    val nonEssentialRunning = remember(processes, settings.protectedPackages) {
        AppInspectorEngine.selectNonEssentialRunningTargets(
            processes = processes,
            protectedPackages = settings.protectedPackages,
            ownPackage = context.packageName
        )
    }
    val totalReclaimableCacheBytes = remember(processes) {
        processes.sumOf { it.cacheBytes }
    }

    val liveRamSparkline = remember { mutableStateListOf<Float>() }
    val liveCpuSparkline = remember { mutableStateListOf<Float>() }

    LaunchedEffect(settings.isLoaded) {
        if (settings.isLoaded && !appliedInitialTab) {
            appliedInitialTab = true
            if (deepLinkReq == null) {
                selectedTab = settings.defaultStartupTab
            }
        }
    }

    LaunchedEffect(deepLinkReq) {
        val req = deepLinkReq ?: return@LaunchedEffect
        if (req.openSettings) {
            showSettingsScreen = true
        } else {
            showSettingsScreen = false
            selectedTab = req.tab
            if (!req.inspectPackage.isNullOrBlank()) {
                pendingInspectPackage = req.inspectPackage
            }
        }
    }

    LaunchedEffect(automationProgress.isRunning, automationProgress.lastSummaryBanner) {
        if (!automationProgress.isRunning && automationProgress.lastSummaryBanner != null) {
            snapshot = SystemTelemetryEngine.sampleSnapshot(context)
            processes = SystemTelemetryEngine.queryProcessCatalog(context)
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
    LaunchedEffect(lifecycleOwner, isLivePolling, settings.pollingIntervalMs) {
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
                delay(settings.pollingIntervalMs)
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

    val launchSystemClearAllCacheDialog: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canUseSystemClearAllDialog) {
            runCatching {
                systemClearCacheLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
            }.onFailure {
                Toast.makeText(context, "System cache dialog unavailable on this ROM", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val triggerStopNonEssential: (String) -> Unit = { returnTab ->
        if (SentinelAutomationService.isEnabled(context)) {
            if (!SentinelAutomationService.startForceStopNonEssential(nonEssentialRunning, returnTab)) {
                pendingSetupPromptTitle = "Process Stopping"
                pendingSetupAllowCacheDialog = false
            }
        } else {
            pendingSetupPromptTitle = "Process Stopping"
            pendingSetupAllowCacheDialog = false
        }
    }

    val triggerForceStopSingle: (InstalledProcessEntry, String) -> Unit = { target, returnTab ->
        if (SentinelAutomationService.isEnabled(context)) {
            if (!SentinelAutomationService.startForceStopNonEssential(listOf(target), returnTab)) {
                openAppDetails(target.packageName)
            }
        } else {
            pendingSetupPromptTitle = "Force Stop (${target.appLabel})"
            pendingSetupAllowCacheDialog = false
        }
    }

    val triggerAutoClearCaches: (List<InstalledProcessEntry>, String) -> Unit = { targets, returnTab ->
        if (SentinelAutomationService.isEnabled(context)) {
            if (!SentinelAutomationService.startClearCaches(targets, returnTab)) {
                pendingSetupPromptTitle = "Cache Cleaning"
                pendingSetupAllowCacheDialog = canUseSystemClearAllDialog
            }
        } else {
            pendingSetupPromptTitle = "Cache Cleaning"
            pendingSetupAllowCacheDialog = canUseSystemClearAllDialog
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

    if (showSettingsScreen) {
        SentinelSettingsScreen(
            settings = settings,
            snapshot = snapshot,
            onOpenReleaseNotes = { showReleaseNotesSheet = true },
            onClose = { showSettingsScreen = false }
        )
    } else {
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
                            val cadenceSec = (settings.pollingIntervalMs / 1000L).coerceAtLeast(1L)
                            Text(
                                text = if (isLivePolling) "Live Telemetry • ${cadenceSec}s cadence" else "Telemetry Paused • 0-FPS Idle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showReleaseNotesSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "What's New & Release Notes",
                            tint = SentinelCyan
                        )
                    }
                    IconButton(onClick = { isLivePolling = !isLivePolling }) {
                        Icon(
                            imageVector = if (isLivePolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLivePolling) "Pause live polling" else "Resume live polling",
                            tint = SentinelEmerald
                        )
                    }
                    IconButton(onClick = { showSettingsScreen = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Themes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    icon = { Icon(Icons.Default.Science, contentDescription = null) },
                    label = { Text("Lab") }
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
                automationProgress = automationProgress,
                nonEssentialRunningCount = nonEssentialRunning.size,
                reclaimableCacheBytes = totalReclaimableCacheBytes,
                isMiniHudRunning = isMiniHudRunning,
                protectedCount = settings.protectedPackages.size,
                useFahrenheit = settings.useFahrenheit,
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
                onStopNonEssentialProcesses = { triggerStopNonEssential("overview") },
                onAutoClearAllCaches = {
                    if (!snapshot.hasUsageStatsAccess && canUseSystemClearAllDialog) {
                        launchSystemClearAllCacheDialog()
                    } else {
                        val hogs = AppInspectorEngine.selectCacheHogTargets(processes, context.packageName)
                        if (hogs.isEmpty() && canUseSystemClearAllDialog) {
                            launchSystemClearAllCacheDialog()
                        } else {
                            triggerAutoClearCaches(hogs, "overview")
                        }
                    }
                },
                onCancelAutomation = { SentinelAutomationService.cancelActiveRun() },
                onDismissAutomationBanner = { SentinelAutomationService.clearBanner() },
                onToggleMiniHud = toggleMiniHud,
                onOpenUsageAccess = openUsageAccess,
                onOpenReleaseNotes = { showReleaseNotesSheet = true },
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.PROCESSES -> ProcessesTab(
                processes = processes,
                nonEssentialRunning = nonEssentialRunning,
                exitSummaries = exitSummaries,
                protectedPackages = settings.protectedPackages,
                automationProgress = automationProgress,
                hasUsageAccess = snapshot.hasUsageStatsAccess,
                compactRows = settings.compactProcessRows,
                deepLinkInspectPackage = pendingInspectPackage,
                onConsumeDeepLinkPackage = { pendingInspectPackage = null },
                onToggleProtected = { pkg ->
                    scope.launch { SentinelPreferencesRepository.toggleProtectedPackage(context, pkg) }
                },
                onStopAllNonEssential = { triggerStopNonEssential("processes") },
                onForceStopSingleApp = { entry -> triggerForceStopSingle(entry, "processes") },
                onClearCacheSingleApp = { entry -> triggerAutoClearCaches(listOf(entry), "processes") },
                onCancelAutomation = { SentinelAutomationService.cancelActiveRun() },
                onDismissAutomationBanner = { SentinelAutomationService.clearBanner() },
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
                automationProgress = automationProgress,
                canUseSystemClearAllDialog = canUseSystemClearAllDialog,
                onAutoClearTopCaches = {
                    val hogs = AppInspectorEngine.selectCacheHogTargets(processes, context.packageName)
                    triggerAutoClearCaches(hogs, "storage")
                },
                onSystemClearAllCaches = launchSystemClearAllCacheDialog,
                onClearSingleAppCache = { entry -> triggerAutoClearCaches(listOf(entry), "storage") },
                onCancelAutomation = { SentinelAutomationService.cancelActiveRun() },
                onDismissAutomationBanner = { SentinelAutomationService.clearBanner() },
                onOpenAppStorage = openAppDetails,
                onGrantUsageAccess = openUsageAccess,
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.SECURITY -> SecurityTab(
                processes = processes,
                firewallBlockedPackages = settings.firewallBlockedPackages,
                sensorBlackboxLog = settings.sensorBlackboxLog,
                onToggleFirewallPackage = { pkg ->
                    scope.launch { SentinelPreferencesRepository.toggleFirewallBlockedPackage(context, pkg) }
                },
                onClearSensorBlackbox = {
                    scope.launch { SentinelPreferencesRepository.clearSensorBlackboxLog(context) }
                },
                onOpenAppDetails = openAppDetails,
                modifier = Modifier.padding(innerPadding)
            )
            SentinelTab.WATCHDOG -> LabAndWatchdogTab(
                snapshot = snapshot,
                processes = processes,
                nonEssentialCount = nonEssentialRunning.size,
                settings = settings,
                isMiniHudRunning = isMiniHudRunning,
                auditBanner = watchdogBanner,
                onToggleWatchdog = { enabled ->
                    scope.launch {
                        SentinelPreferencesRepository.setWatchdogEnabled(context, enabled)
                        SentinelWatchdogJobService.scheduleOrCancel(context, enabled)
                    }
                },
                onToggleChargeLimitAlarm = { enabled, limitPct ->
                    scope.launch {
                        SentinelPreferencesRepository.setChargeLimitAlarm(context, enabled, limitPct)
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

    if (pendingSetupPromptTitle != null) {
        AutomationSetupBottomSheet(
            requestedActionTitle = pendingSetupPromptTitle!!,
            showSystemCacheFallback = pendingSetupAllowCacheDialog,
            onOpenAccessibilitySettings = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
            onOpenOwnAppInfo = { openAppDetails(context.packageName) },
            onUseSystemClearCacheDialog = launchSystemClearAllCacheDialog,
            onDismiss = { pendingSetupPromptTitle = null }
        )
    }

    if (showReleaseNotesSheet) {
        ReleaseNotesBottomSheet(
            onDismiss = {
                showReleaseNotesSheet = false
                scope.launch {
                    SentinelPreferencesRepository.markReleaseVersionSeen(
                        context,
                        SentinelReleaseNotes.CURRENT_VERSION
                    )
                }
            }
        )
    }
}
