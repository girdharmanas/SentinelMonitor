package com.sentinel.monitor.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sentinel.monitor.data.model.CableGraderReport
import com.sentinel.monitor.data.model.CodecDrmProfile
import com.sentinel.monitor.data.model.HopDiagnosticReport
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.preferences.SentinelSettings
import com.sentinel.monitor.service.FlightRecorderManager
import com.sentinel.monitor.ui.components.TelemetrySparkline
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCrimson
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import com.sentinel.monitor.util.NetworkAndHardwareLabEngine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class LabSubMode(val label: String) {
    DIAGNOSTICS("Hardware Lab"),
    NETWORK_DNS("Net & DNS"),
    WATCHDOG("Watchdog")
}

@Composable
fun LabAndWatchdogTab(
    snapshot: SystemSnapshot,
    processes: List<InstalledProcessEntry>,
    nonEssentialCount: Int,
    settings: SentinelSettings,
    isMiniHudRunning: Boolean,
    auditBanner: String?,
    onToggleWatchdog: (Boolean) -> Unit,
    onToggleChargeLimitAlarm: (Boolean, Int) -> Unit,
    onUpdateThresholds: (Float, Int, Float) -> Unit,
    onRunWatchdogNow: () -> Unit,
    onToggleMiniHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subMode by rememberSaveable { mutableStateOf(LabSubMode.DIAGNOSTICS) }

    var codecProfile by remember { mutableStateOf<CodecDrmProfile?>(null) }
    var cableReport by remember { mutableStateOf<CableGraderReport?>(null) }
    var isTestingCable by remember { mutableStateOf(false) }
    var cableProgressPct by remember { mutableIntStateOf(0) }

    var hopReport by remember { mutableStateOf<HopDiagnosticReport?>(null) }
    var isRunningHopDiag by remember { mutableStateOf(false) }
    var showDisplayBenchDialog by remember { mutableStateOf(false) }

    val isFlightRecording by FlightRecorderManager.isRecordingFlow.collectAsState()
    val flightElapsedSec by FlightRecorderManager.elapsedSecondsFlow.collectAsState()
    val latestFlightReport by FlightRecorderManager.latestReportFlow.collectAsState()

    LaunchedEffect(Unit) {
        codecProfile = NetworkAndHardwareLabEngine.inspectCodecAndDrmProfile()
    }

    LaunchedEffect(subMode) {
        if (subMode == LabSubMode.NETWORK_DNS && hopReport == null && !isRunningHopDiag) {
            isRunningHopDiag = true
            hopReport = NetworkAndHardwareLabEngine.runHopAndDnsDiagnostics(context)
            isRunningHopDiag = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            LabSubMode.entries.forEach { mode ->
                FilterChip(
                    selected = subMode == mode,
                    onClick = { subMode = mode },
                    label = { Text(mode.label, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (subMode) {
            LabSubMode.DIAGNOSTICS -> {
                val sensorReadout = rememberLiveEnvironmentSensors(context)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. 1-Tap Device Health Markdown Dossier
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Sentinel Device Health Dossier",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Export a complete Markdown report of SoC, Deep-Sleep %, RAM/ZRAM, Battery/Wattage, Widevine DRM, Hardware Codecs & Security Audit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val md = NetworkAndHardwareLabEngine.buildDeviceHealthMarkdownDossier(
                                                snapshot = snapshot,
                                                codecProfile = codecProfile,
                                                processes = processes,
                                                nonEssentialCount = nonEssentialCount
                                            )
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Sentinel Dossier", md))
                                            Toast.makeText(context, "Copied Markdown Dossier to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy Markdown")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val md = NetworkAndHardwareLabEngine.buildDeviceHealthMarkdownDossier(
                                                snapshot = snapshot,
                                                codecProfile = codecProfile,
                                                processes = processes,
                                                nonEssentialCount = nonEssentialCount
                                            )
                                            runCatching {
                                                val send = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Sentinel Device Health Dossier")
                                                    putExtra(Intent.EXTRA_TEXT, md)
                                                }
                                                context.startActivity(Intent.createChooser(send, "Share Health Dossier"))
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }

                    // 2. USB-C Cable & Charger Quality Grader
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = SentinelAmber)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "USB-C Cable & Charger Quality Grader",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Samples BatteryManager voltage rail sag (mV) and negotiated PD/PPS Wattage under load.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isTestingCable) {
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { cableProgressPct / 100f },
                                        color = SentinelAmber,
                                        trackColor = SentinelSurfaceHigh,
                                        strokeCap = StrokeCap.Round,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                }
                                if (cableReport != null) {
                                    val rep = cableReport!!
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = rep.gradeTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rep.isCharging) SentinelEmerald else SentinelAmber
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Peak %.1f W (Avg %.1f W • %.0f mA) • Rail %d–%d mV (%d mV swing)",
                                            rep.peakWatts,
                                            rep.avgWatts,
                                            rep.avgCurrentMa,
                                            rep.voltageMinMv,
                                            rep.voltageMaxMv,
                                            rep.voltageSwingMv
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = SentinelCyan
                                    )
                                    Text(
                                        text = rep.verdict,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (!isTestingCable) {
                                            isTestingCable = true
                                            cableProgressPct = 10
                                            scope.launch {
                                                cableReport = NetworkAndHardwareLabEngine.runCableAndChargerTest(context) { pct ->
                                                    cableProgressPct = pct
                                                }
                                                isTestingCable = false
                                            }
                                        }
                                    },
                                    enabled = !isTestingCable,
                                    colors = ButtonDefaults.buttonColors(containerColor = SentinelAmber),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isTestingCable) "Sampling Rail Stability ($cableProgressPct%)…" else "Run 4s Cable & Brick Benchmark")
                                }
                            }
                        }
                    }

                    // 3. Game / Benchmark "Flight Recorder" Session Mode
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = SentinelCyan)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Benchmark & Game Flight Recorder",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (isFlightRecording) {
                                        "Recording live session (${flightElapsedSec}s elapsed)… Switch to any game/app and return here to stop."
                                    } else {
                                        "Record CPU MHz, RAM pressure, thermal throttling & mAh draw across a gaming or benchmark run."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFlightRecording) SentinelEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (latestFlightReport != null && !isFlightRecording) {
                                    val fr = latestFlightReport!!
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Last Run (${fr.durationSec}s): Avg ${fr.avgCpuMhz} MHz (Peak ${fr.peakCpuMhz} MHz) • Peak ${fr.peakRamPercent}% RAM • ${fr.startTempC}°C → ${fr.peakTempC}°C (${fr.thermalThrottleHits} throttles) • Avg ${String.format(Locale.US, "%.1f", fr.avgPowerWatts)} W",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SentinelCyan
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (isFlightRecording) {
                                                FlightRecorderManager.stopRecording()
                                            } else {
                                                FlightRecorderManager.startRecording(context)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFlightRecording) SentinelCrimson else SentinelCyan
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isFlightRecording) "Stop Flight Recorder (${flightElapsedSec}s)" else "Start Session Recording")
                                    }
                                    if (latestFlightReport != null && !isFlightRecording) {
                                        OutlinedButton(
                                            onClick = {
                                                val md = latestFlightReport!!.toMarkdown(snapshot.deviceModel)
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(ClipData.newPlainText("Flight Report", md))
                                                Toast.makeText(context, "Copied Flight Report Markdown", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Copy MD")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Widevine DRM & Hardware Codec Silicon Check
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Hd, contentDescription = null, tint = SentinelEmerald)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Widevine DRM & Silicon Video Decoders",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                val cp = codecProfile
                                if (cp == null) {
                                    Text("Inspecting MediaDrm & MediaCodecList…", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(
                                        text = "Widevine Security: ${cp.widevineLevel} (${cp.widevineVendor})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (cp.widevineLevel.equals("L1", ignoreCase = true)) SentinelEmerald else SentinelAmber
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "HW AV1: ${if (cp.hasHardwareAv1) "✓ Yes" else "Software"} • HW HEVC 10-bit: ${if (cp.hasHardwareHevc10Bit) "✓ Yes" else "No"} • HW VP9: ${if (cp.hasHardwareVp9) "✓ Yes" else "No"} • ${cp.hardwareVideoDecoderCount} HW Decoders",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 5. Display OLED Uniformity, Touch-Hz Bench & Environment Sensors
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = SentinelCyan)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "OLED Uniformity, Touch Hz & Sensors",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = sensorReadout,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = SentinelEmerald
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { showDisplayBenchDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Launch Fullscreen OLED & Multi-Touch Hz Test")
                                }
                            }
                        }
                    }

                    // 6. Hidden Android Power-User Shortcut Deck
                    item {
                        PowerUserShortcutDeck(context = context)
                    }
                }
            }

            LabSubMode.NETWORK_DNS -> {
                val topDataApps = remember(processes) {
                    processes.filter { it.totalNetworkBytes24h > 0L }
                        .sortedByDescending { it.totalNetworkBytes24h }
                        .take(15)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = SentinelCyan)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "3-Hop Fault Isolator & DNS Speed Race",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                val rep = hopReport
                                if (rep != null) {
                                    Text(
                                        text = rep.verdict,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rep.verdict.startsWith("All Hops Healthy")) SentinelEmerald else SentinelAmber
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Hop 1 Router (${rep.gatewayIp}): ${rep.gatewayLatencyMs?.let { "${it}ms" } ?: "—"} • Hop 2 WAN IP: ${rep.ispRawIpLatencyMs?.let { "${it}ms" } ?: "—"} • Hop 3 DNS: ${rep.dnsResolveLatencyMs?.let { "${it}ms" } ?: "—"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = SentinelCyan
                                    )
                                    Text(
                                        text = "Local IP: ${rep.localIp} • MTU ${rep.mtu} • Private DNS: ${rep.privateDnsMode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (rep.dnsRace.isNotEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        rep.dnsRace.forEach { dns ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "${dns.providerName} (${dns.serverAddress})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = if (dns.isReachable) "${dns.latencyMs} ms" else "Blocked/Timeout",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (dns.isReachable) SentinelEmerald else SentinelCrimson,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Measuring Router Gateway, Raw WAN IP & UDP DNS resolvers…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (!isRunningHopDiag) {
                                            isRunningHopDiag = true
                                            scope.launch {
                                                hopReport = NetworkAndHardwareLabEngine.runHopAndDnsDiagnostics(context)
                                                isRunningHopDiag = false
                                            }
                                        }
                                    },
                                    enabled = !isRunningHopDiag,
                                    colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isRunningHopDiag) "Pinging Hops & Racing DNS…" else "Re-Run 3-Hop & DNS Race")
                                }
                            }
                        }
                    }

                    if (topDataApps.isNotEmpty()) {
                        item {
                            Text(
                                text = "Top 24h Network Data Consumers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(topDataApps, key = { it.packageName }) { app ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Wi-Fi ${TelemetryFormatters.formatBytes(app.wifiDataBytes24h)} • Cellular ${TelemetryFormatters.formatBytes(app.mobileDataBytes24h)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = TelemetryFormatters.formatBytes(app.totalNetworkBytes24h),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SentinelCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LabSubMode.WATCHDOG -> {
                WatchdogSettingsColumn(
                    settings = settings,
                    isMiniHudRunning = isMiniHudRunning,
                    auditBanner = auditBanner,
                    onToggleWatchdog = onToggleWatchdog,
                    onToggleChargeLimitAlarm = onToggleChargeLimitAlarm,
                    onUpdateThresholds = onUpdateThresholds,
                    onRunWatchdogNow = onRunWatchdogNow,
                    onToggleMiniHud = onToggleMiniHud
                )
            }
        }
    }

    if (showDisplayBenchDialog) {
        FullscreenDisplayAndTouchDialog(onDismiss = { showDisplayBenchDialog = false })
    }
}

@Composable
private fun WatchdogSettingsColumn(
    settings: SentinelSettings,
    isMiniHudRunning: Boolean,
    auditBanner: String?,
    onToggleWatchdog: (Boolean) -> Unit,
    onToggleChargeLimitAlarm: (Boolean, Int) -> Unit,
    onUpdateThresholds: (Float, Int, Float) -> Unit,
    onRunWatchdogNow: () -> Unit,
    onToggleMiniHud: () -> Unit
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
    var chargeSlider by remember(settings.chargeLimitPercent) {
        mutableFloatStateOf(settings.chargeLimitPercent.toFloat())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                            text = "Tap 'Sample & Check Watchdog Now' below to populate your 24h trend curves.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Battery 80% Charge Limit Alarm Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = SentinelEmerald)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Charge Limit Alarm (${chargeSlider.roundToInt()}%)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Alert when charging reaches target ceiling to reduce Li-ion high-voltage wear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.chargeLimitAlarmEnabled,
                            onCheckedChange = { enabled ->
                                onToggleChargeLimitAlarm(enabled, chargeSlider.roundToInt())
                            }
                        )
                    }
                    Slider(
                        value = chargeSlider,
                        onValueChange = { chargeSlider = it },
                        onValueChangeFinished = {
                            onToggleChargeLimitAlarm(settings.chargeLimitAlarmEnabled, chargeSlider.roundToInt())
                        },
                        valueRange = 70f..95f
                    )
                }
            }
        }

        // Proactive Background Watchdog
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
                    val thermalLimitStr = TelemetryFormatters.formatTemperature(thermalSlider, settings.useFahrenheit)
                    Text(
                        text = "Thermal Alert Limit: $thermalLimitStr",
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
                            text = "Glanceable Surfaces & Suite Bridge",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "• Floating Developer Mini-HUD: Draggable live pill (MHz • RAM • W • °C • Hz) over any app.\n• Quick Settings Tile: Add 'Sentinel Sweep' to your notification shade.\n• Sentinel Launcher Focus Bridge: Auto-sweeps background memory when Focus Space activates.",
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

@Composable
private fun PowerUserShortcutDeck(context: Context) {
    val shortcuts = remember {
        listOf(
            "Developer Options" to Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            "Notification History" to Intent("android.settings.NOTIFICATION_HISTORY"),
            "Wireless Debugging" to Intent("android.settings.WIFI_ADB_SETTINGS"),
            "Battery Optimization" to Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            "Private DNS / Net" to Intent(Settings.ACTION_WIRELESS_SETTINGS),
            "Default Apps" to Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            "VPN Profiles" to Intent(Settings.ACTION_VPN_SETTINGS),
            "Radio / Phone Info" to Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo")
            }
        )
    }

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
                Icon(Icons.Default.Build, contentDescription = null, tint = SentinelAmber)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Hidden Android Power-User Shortcuts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            shortcuts.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { (title, intent) ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                    Toast.makeText(context, "$title unavailable on this OEM ROM", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLiveEnvironmentSensors(context: Context): String {
    var luxVal by remember { mutableStateOf<Float?>(null) }
    var hpaVal by remember { mutableStateOf<Float?>(null) }
    var sensorCount by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorCount = sm?.getSensorList(Sensor.TYPE_ALL)?.size ?: 0
        val light = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val pressure = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val ev = event ?: return
                when (ev.sensor.type) {
                    Sensor.TYPE_LIGHT -> luxVal = ev.values.firstOrNull()
                    Sensor.TYPE_PRESSURE -> hpaVal = ev.values.firstOrNull()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (light != null) sm.registerListener(listener, light, SensorManager.SENSOR_DELAY_UI)
        if (pressure != null) sm.registerListener(listener, pressure, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sm?.unregisterListener(listener)
        }
    }

    val luxStr = luxVal?.let { "${it.roundToInt()} lx" } ?: "N/A"
    val baroStr = hpaVal?.let { String.format(Locale.US, "%.1f hPa", it) } ?: "N/A"
    return "Ambient Light: $luxStr • Barometer: $baroStr • $sensorCount HW Sensors"
}

@Composable
private fun FullscreenDisplayAndTouchDialog(onDismiss: () -> Unit) {
    val swatches = remember {
        listOf(
            "Pure OLED Black (#000000)" to Color(0xFF000000),
            "5% Near-Black Gray (OLED Banding)" to Color(0xFF0D0D0D),
            "Pure Red Subpixel (#FF0000)" to Color(0xFFFF0000),
            "Pure Green Subpixel (#00FF00)" to Color(0xFF00FF00),
            "Pure Blue Subpixel (#0000FF)" to Color(0xFF0000FF),
            "100% Full White Uniformity (#FFFFFF)" to Color(0xFFFFFFFF)
        )
    }
    var swatchIndex by remember { mutableIntStateOf(0) }
    var pointerCount by remember { mutableIntStateOf(0) }
    var measuredTouchHz by remember { mutableIntStateOf(0) }

    val (swatchLabel, bgColor) = swatches[swatchIndex % swatches.size]
    val fgColor = if (swatchIndex % swatches.size == 5) Color.Black else Color.White

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var lastMoveMs = 0L
                        var eventTapMoved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.count { it.pressed }
                            pointerCount = active
                            if (active == 0) {
                                if (!eventTapMoved) {
                                    swatchIndex = (swatchIndex + 1) % swatches.size
                                }
                                break
                            }
                            val now = SystemClock.elapsedRealtime()
                            val moved = event.changes.any {
                                (it.position - it.previousPosition).getDistance() > 3f
                            }
                            if (moved) {
                                eventTapMoved = true
                                if (lastMoveMs > 0L && now > lastMoveMs) {
                                    val hz = (1000L / (now - lastMoveMs)).toInt().coerceIn(30, 360)
                                    measuredTouchHz = hz
                                }
                                lastMoveMs = now
                            }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = swatchLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = fgColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap screen to cycle color • Drag finger to measure Touch Sampling Rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = fgColor.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Active Fingers: $pointerCount • Touch Sampling: ${if (measuredTouchHz > 0) "$measuredTouchHz Hz" else "Drag to measure"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = fgColor
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Display Test", tint = fgColor)
            }
        }
    }
}
