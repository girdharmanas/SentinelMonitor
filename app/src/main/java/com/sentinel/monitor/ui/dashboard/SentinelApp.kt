package com.sentinel.monitor.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelObsidian
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import com.sentinel.monitor.ui.theme.accentColor
import com.sentinel.monitor.util.SystemTelemetryEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isLivePolling by rememberSaveable { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf(SystemSnapshot()) }
    var processes by remember { mutableStateOf<List<InstalledProcessEntry>>(emptyList()) }
    var isSweeping by remember { mutableStateOf(false) }
    var sweepBanner by remember { mutableStateOf<String?>(null) }

    // Lifecycle-gated zero-overhead polling when backgrounded
    LaunchedEffect(lifecycleOwner, isLivePolling) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshot = SystemTelemetryEngine.sampleSnapshot(context)
            if (processes.isEmpty()) {
                processes = SystemTelemetryEngine.queryProcessCatalog(context)
            }
            while (isLivePolling) {
                delay(2000L)
                snapshot = SystemTelemetryEngine.sampleSnapshot(context)
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
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        scope.launch {
                            processes = SystemTelemetryEngine.queryProcessCatalog(context)
                        }
                    },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    label = { Text("Processes") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text("Storage") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> OverviewTab(
                snapshot = snapshot,
                isSweeping = isSweeping,
                sweepBanner = sweepBanner,
                onRunMemorySweep = {
                    if (!isSweeping) {
                        isSweeping = true
                        scope.launch {
                            val report = SystemTelemetryEngine.performSafeMemorySweep(context)
                            snapshot = SystemTelemetryEngine.sampleSnapshot(context)
                            sweepBanner = report.summaryLabel()
                            isSweeping = false
                        }
                    }
                },
                onOpenUsageAccess = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> ProcessesTab(
                processes = processes,
                hasUsageAccess = snapshot.hasUsageStatsAccess,
                onOpenAppDetails = { pkg ->
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", pkg, null)
                        }
                        context.startActivity(intent)
                    }
                },
                onGrantUsageAccess = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            2 -> StorageTab(
                snapshot = snapshot,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun OverviewTab(
    snapshot: SystemSnapshot,
    isSweeping: Boolean,
    sweepBanner: String?,
    onRunMemorySweep: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ramRag = TelemetryRag.fromUsageFraction(snapshot.ramUsageFraction)
    val storageRag = TelemetryRag.fromUsageFraction(snapshot.storageUsageFraction)
    val thermalRag = TelemetryRag.fromBatteryTempCelsius(snapshot.batteryTempCelsius)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TelemetryGaugeCard(
                title = "RAM & Physical Memory",
                icon = Icons.Default.Memory,
                headline = "${(snapshot.ramUsageFraction * 100).toInt()}% Used",
                subtitle = "${TelemetryFormatters.formatBytes(snapshot.availableRamBytes)} free of ${TelemetryFormatters.formatBytes(snapshot.totalRamBytes)}",
                progress = snapshot.ramUsageFraction,
                rag = ramRag
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SentinelEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = SentinelEmerald
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Safe Background Sweep",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Reclaims cached background processes while protecting launcher, IME, and system services.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onRunMemorySweep,
                        enabled = !isSweeping,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSweeping) "Sweeping Background Processes…" else "Sweep Background Memory")
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

        item {
            TelemetryGaugeCard(
                title = "Internal Storage",
                icon = Icons.Default.Storage,
                headline = "${(snapshot.storageUsageFraction * 100).toInt()}% Occupied",
                subtitle = "${TelemetryFormatters.formatBytes(snapshot.freeStorageBytes)} available of ${TelemetryFormatters.formatBytes(snapshot.totalStorageBytes)}",
                progress = snapshot.storageUsageFraction,
                rag = storageRag
            )
        }

        item {
            TelemetryGaugeCard(
                title = "Battery & Thermal Envelope",
                icon = Icons.Default.BatteryChargingFull,
                headline = "${snapshot.batteryPercent}% • ${snapshot.batteryTempCelsius}°C",
                subtitle = if (snapshot.isCharging) "Charging • Healthy voltage" else "Discharging on battery",
                progress = (snapshot.batteryPercent / 100f).coerceIn(0f, 1f),
                rag = thermalRag
            )
        }

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
                        text = "Hardware & Kernel Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${snapshot.deviceModel} • ${snapshot.androidVersionLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${snapshot.cpuCoreCount} CPU Cores (${snapshot.primaryAbi}) • Uptime ${TelemetryFormatters.formatUptime(snapshot.uptimeMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!snapshot.hasUsageStatsAccess) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Grant Usage Access to unlock 24h per-app foreground activity attribution →",
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

@Composable
private fun TelemetryGaugeCard(
    title: String,
    icon: ImageVector,
    headline: String,
    subtitle: String,
    progress: Float,
    rag: TelemetryRag
) {
    val accent = rag.accentColor()
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SentinelSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = rag.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = accent,
                trackColor = SentinelSurfaceHigh,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

@Composable
private fun ProcessesTab(
    processes: List<InstalledProcessEntry>,
    hasUsageAccess: Boolean,
    onOpenAppDetails: (String) -> Unit,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(processes, query, showSystemApps) {
        processes.filter { entry ->
            (showSystemApps || !entry.isSystemApp) &&
                (query.isBlank() ||
                    entry.appLabel.contains(query, ignoreCase = true) ||
                    entry.packageName.contains(query, ignoreCase = true))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Filter apps or package names…") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            FilterChip(
                selected = !showSystemApps,
                onClick = { showSystemApps = false },
                label = { Text("User Apps") }
            )
            FilterChip(
                selected = showSystemApps,
                onClick = { showSystemApps = true },
                label = { Text("Include System") }
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${filtered.size} packages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!hasUsageAccess) {
            Text(
                text = "Tip: Enable Usage Access to rank apps by 24h active foreground CPU window →",
                style = MaterialTheme.typography.labelSmall,
                color = SentinelEmerald,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable { onGrantUsageAccess() }
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.packageName }) { item ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAppDetails(item.packageName) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.appLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        val fgMinutes = item.totalForegroundMillis24h / 60_000L
                        Text(
                            text = if (fgMinutes > 0L) "${fgMinutes}m fg" else "Inspect",
                            style = MaterialTheme.typography.labelMedium,
                            color = SentinelEmerald
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageTab(
    snapshot: SystemSnapshot,
    modifier: Modifier = Modifier
) {
    val rag = TelemetryRag.fromUsageFraction(snapshot.storageUsageFraction)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TelemetryGaugeCard(
            title = "User Data Partition (/data)",
            icon = Icons.Default.Storage,
            headline = "${TelemetryFormatters.formatBytes(snapshot.usedStorageBytes)} Used",
            subtitle = "${TelemetryFormatters.formatBytes(snapshot.freeStorageBytes)} free • Total ${TelemetryFormatters.formatBytes(snapshot.totalStorageBytes)}",
            progress = snapshot.storageUsageFraction,
            rag = rag
        )
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
                    text = "Storage Health Guidance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Keeping at least 15% free space on /data prevents SQLite WAL contention, F2FS garbage-collection stalls, and OTA package extraction failures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SentinelMonitorCrestBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.24f)
            cubicTo(w * 0.88f, h * 0.58f, w * 0.74f, h * 0.82f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.26f, h * 0.82f, w * 0.12f, h * 0.58f, w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF132A31), Color(0xFF0B1522)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF34D399), Color(0xFF38BDF8)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Stroke(width = w * 0.075f, join = StrokeJoin.Round)
        )
        val pulse = Path().apply {
            moveTo(w * 0.25f, h * 0.52f)
            lineTo(w * 0.39f, h * 0.52f)
            lineTo(w * 0.46f, h * 0.31f)
            lineTo(w * 0.55f, h * 0.70f)
            lineTo(w * 0.63f, h * 0.43f)
            lineTo(w * 0.69f, h * 0.52f)
            lineTo(w * 0.75f, h * 0.52f)
        }
        drawPath(
            path = pulse,
            color = Color(0xFFA7F3D0),
            style = Stroke(width = w * 0.068f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
