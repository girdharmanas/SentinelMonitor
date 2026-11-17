package com.sentinel.monitor.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

enum class SentinelTab(val routeKey: String, val label: String) {
    VITALS("overview", "Vitals"),
    PROCESSES("processes", "Processes"),
    STORAGE("storage", "Storage"),
    SECURITY("security", "Security"),
    WATCHDOG("watchdog", "Lab");

    companion object {
        fun fromQueryParam(param: String?): SentinelTab {
            return when (param?.trim()?.lowercase(Locale.US)) {
                "processes", "apps", "ram", "memory", "xray" -> PROCESSES
                "storage", "cache", "disk", "scavenger" -> STORAGE
                "security", "permissions", "audit", "firewall", "blackbox" -> SECURITY
                "watchdog", "alerts", "hud", "history", "lab", "ping", "dns", "cable", "benchmark" -> WATCHDOG
                else -> VITALS
            }
        }
    }
}

data class SentinelDeepLinkRequest(
    val tab: SentinelTab,
    val inspectPackage: String? = null,
    val openSettings: Boolean = false,
    val requestNonce: Long = System.currentTimeMillis()
)

enum class TelemetryRag {
    HEALTHY,
    ELEVATED,
    CRITICAL;

    companion object {
        fun fromUsageFraction(fraction: Float): TelemetryRag {
            val clamped = fraction.coerceIn(0f, 1f)
            return when {
                clamped >= 0.85f -> CRITICAL
                clamped >= 0.68f -> ELEVATED
                else -> HEALTHY
            }
        }

        fun fromBatteryTempCelsius(tempC: Float): TelemetryRag {
            return when {
                tempC >= 41.5f -> CRITICAL
                tempC >= 37.5f -> ELEVATED
                else -> HEALTHY
            }
        }
    }
}

data class CpuCoreSample(
    val coreIndex: Int,
    val curFreqKhz: Long,
    val minFreqKhz: Long,
    val maxFreqKhz: Long,
    val governor: String = "schedutil"
) {
    val utilizationFraction: Float
        get() = when {
            maxFreqKhz <= 0L || curFreqKhz <= 0L -> 0f
            maxFreqKhz > minFreqKhz ->
                ((curFreqKhz - minFreqKhz).toDouble() / (maxFreqKhz - minFreqKhz).toDouble())
                    .toFloat().coerceIn(0.04f, 1f)
            else -> (curFreqKhz.toDouble() / maxFreqKhz.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val curFreqMhz: Int
        get() = (curFreqKhz / 1000L).toInt().coerceAtLeast(0)

    val maxFreqMhz: Int
        get() = (maxFreqKhz / 1000L).toInt().coerceAtLeast(0)
}

data class SystemSnapshot(
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val cachedRamBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val swapFreeBytes: Long = 0L,
    val isLowMemory: Boolean = false,
    val totalStorageBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val batteryPercent: Int = 100,
    val batteryTempCelsius: Float = 28.0f,
    val batteryVoltageMv: Int = 4000,
    val batteryCurrentMa: Float = 0f,
    val batteryPowerWatts: Float = 0f,
    val batteryCycleCount: Int = -1,
    val isCharging: Boolean = false,
    val thermalStatusLabel: String = "Nominal",
    val thermalSeverityLevel: Int = 0,
    val cpuCoreCount: Int = 1,
    val cpuCores: List<CpuCoreSample> = emptyList(),
    val cpuGovernor: String = "schedutil",
    val netRxBytesPerSec: Long = 0L,
    val netTxBytesPerSec: Long = 0L,
    val netTransportLabel: String = "Offline",
    val wifiBandOrLinkLabel: String = "",
    val isVpnActive: Boolean = false,
    val displayRefreshHz: Int = 60,
    val socModel: String = "",
    val deviceModel: String = "Android Device",
    val androidVersionLabel: String = "Android",
    val primaryAbi: String = "arm64-v8a",
    val uptimeMillis: Long = 0L,
    val awakeMillis: Long = 0L,
    val hasUsageStatsAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val sampledAtMillis: Long = 0L
) {
    val usedRamBytes: Long
        get() = (totalRamBytes - availableRamBytes).coerceAtLeast(0L)

    val ramUsageFraction: Float
        get() = if (totalRamBytes > 0L) {
            (usedRamBytes.toDouble() / totalRamBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val swapUsedBytes: Long
        get() = (swapTotalBytes - swapFreeBytes).coerceAtLeast(0L)

    val swapUsageFraction: Float
        get() = if (swapTotalBytes > 0L) {
            (swapUsedBytes.toDouble() / swapTotalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val usedStorageBytes: Long
        get() = (totalStorageBytes - freeStorageBytes).coerceAtLeast(0L)

    val storageUsageFraction: Float
        get() = if (totalStorageBytes > 0L) {
            (usedStorageBytes.toDouble() / totalStorageBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val avgCpuUtilizationFraction: Float
        get() = if (cpuCores.isNotEmpty()) {
            cpuCores.map { it.utilizationFraction }.average().toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val peakCpuMhz: Int
        get() = cpuCores.maxOfOrNull { it.curFreqMhz } ?: 0

    val deepSleepMillis: Long
        get() = (uptimeMillis - awakeMillis).coerceAtLeast(0L)

    val deepSleepPercent: Int
        get() = if (uptimeMillis > 0L) {
            ((deepSleepMillis.toDouble() / uptimeMillis.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else {
            0
        }
}

data class VitalsHistoryPoint(
    val timestampMs: Long,
    val ramFraction: Float,
    val batteryPercent: Int,
    val tempCelsius: Float,
    val powerWatts: Float,
    val cpuFraction: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestampMs)
        put("ram", ramFraction.toDouble())
        put("bat", batteryPercent)
        put("tmp", tempCelsius.toDouble())
        put("w", powerWatts.toDouble())
        put("cpu", cpuFraction.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): VitalsHistoryPoint = VitalsHistoryPoint(
            timestampMs = obj.optLong("ts", 0L),
            ramFraction = obj.optDouble("ram", 0.0).toFloat(),
            batteryPercent = obj.optInt("bat", 100),
            tempCelsius = obj.optDouble("tmp", 28.0).toFloat(),
            powerWatts = obj.optDouble("w", 0.0).toFloat(),
            cpuFraction = obj.optDouble("cpu", 0.0).toFloat()
        )

        fun decodeList(jsonStr: String): List<VitalsHistoryPoint> {
            if (jsonStr.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(jsonStr)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(fromJson(it)) }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun encodeList(points: List<VitalsHistoryPoint>, maxPoints: Int = 96): String {
            val trimmed = if (points.size > maxPoints) points.takeLast(maxPoints) else points
            val arr = JSONArray()
            for (pt in trimmed) arr.put(pt.toJson())
            return arr.toString()
        }
    }
}

data class SensorBlackboxEvent(
    val timestampMs: Long,
    val sensorType: String, // "CAMERA" or "MIC"
    val durationMillis: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestampMs)
        put("type", sensorType)
        put("dur", durationMillis)
    }

    companion object {
        fun decodeList(jsonStr: String): List<SensorBlackboxEvent> {
            if (jsonStr.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(jsonStr)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(
                            SensorBlackboxEvent(
                                timestampMs = obj.optLong("ts", 0L),
                                sensorType = obj.optString("type", "MIC"),
                                durationMillis = obj.optLong("dur", 0L)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun encodeList(events: List<SensorBlackboxEvent>, maxEvents: Int = 60): String {
            val trimmed = if (events.size > maxEvents) events.takeLast(maxEvents) else events
            val arr = JSONArray()
            for (ev in trimmed) arr.put(ev.toJson())
            return arr.toString()
        }
    }
}

enum class SensitivePermBadge(val shortLabel: String, val riskCategory: String) {
    ACCESSIBILITY("Accessibility", "Full UI Control"),
    NOTIFICATION_LISTENER("Notif Listener", "Reads Notifications"),
    INSTALL_PACKAGES("Install APKs", "Sideload Installer"),
    OVERLAY("Draw Over Apps", "Screen Overlay"),
    ALL_FILES("All Files", "Unrestricted Storage"),
    BACKGROUND_LOCATION("BG Location", "24/7 Tracking"),
    CAMERA("Camera", "Optical Sensor"),
    MICROPHONE("Microphone", "Audio Capture")
}

data class InstalledProcessEntry(
    val packageName: String,
    val appLabel: String,
    val versionName: String = "",
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean = false,
    val isStopped: Boolean = false,
    val isRecentlyActive: Boolean,
    val lastTimeUsedMillis: Long,
    val totalForegroundMillis24h: Long,
    val standbyBucketLabel: String = "NORMAL",
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val wifiDataBytes24h: Long = 0L,
    val mobileDataBytes24h: Long = 0L,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isBootAutoStart: Boolean = false,
    val installerSourceLabel: String = "System",
    val isSideloaded: Boolean = false,
    val sensitivePermissions: List<SensitivePermBadge> = emptyList()
) {
    val totalStorageBytes: Long
        get() = appBytes + dataBytes + cacheBytes

    val totalNetworkBytes24h: Long
        get() = wifiDataBytes24h + mobileDataBytes24h

    val isLegacyTargetSdk: Boolean
        get() = !isSystemApp && targetSdk in 1..30
}

data class AppXRayDetails(
    val entry: InstalledProcessEntry,
    val versionCode: Long,
    val firstInstallTimeMs: Long,
    val lastUpdateTimeMs: Long,
    val nativeAbis: List<String>,
    val detectedTrackers: List<String> = emptyList(),
    val sha1Fingerprint: String,
    val sha256Fingerprint: String,
    val sourceApkDir: String
)

data class ProcessExitSummary(
    val packageName: String,
    val processName: String,
    val reasonLabel: String,
    val description: String,
    val timestampMs: Long,
    val pssKb: Long
)

data class ScavengerFileItem(
    val absolutePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val category: String, // "INSTALLER" | "LARGE_FILE" | "DUPLICATE"
    val subtitle: String
)

data class CableGraderReport(
    val isCharging: Boolean,
    val avgWatts: Float,
    val peakWatts: Float,
    val avgCurrentMa: Float,
    val voltageMinMv: Int,
    val voltageMaxMv: Int,
    val gradeTitle: String,
    val verdict: String
) {
    val voltageSwingMv: Int
        get() = (voltageMaxMv - voltageMinMv).coerceAtLeast(0)
}

data class FlightSessionReport(
    val durationSec: Int,
    val sampleCount: Int,
    val avgCpuMhz: Int,
    val peakCpuMhz: Int,
    val avgRamPercent: Int,
    val peakRamPercent: Int,
    val startTempC: Float,
    val peakTempC: Float,
    val thermalThrottleHits: Int,
    val avgPowerWatts: Float,
    val estimatedMahUsed: Float
) {
    fun toMarkdown(deviceModel: String): String = buildString {
        appendLine("## Sentinel Flight Recorder Session ($deviceModel)")
        appendLine("- **Duration:** ${durationSec}s ($sampleCount samples)")
        appendLine("- **CPU Clocks:** Avg $avgCpuMhz MHz • Peak $peakCpuMhz MHz")
        appendLine("- **Memory Pressure:** Avg $avgRamPercent% • Peak $peakRamPercent%")
        appendLine(String.format(Locale.US, "- **Thermal Envelope:** %.1f°C → Peak %.1f°C (%d throttle events)", startTempC, peakTempC, thermalThrottleHits))
        appendLine(String.format(Locale.US, "- **Power Draw:** Avg %.2f W • Est. %.1f mAh", avgPowerWatts, estimatedMahUsed))
    }
}

data class DnsRaceEntry(
    val providerName: String,
    val serverAddress: String,
    val latencyMs: Int,
    val isReachable: Boolean
)

data class HopDiagnosticReport(
    val localIp: String,
    val gatewayIp: String,
    val dnsServers: List<String>,
    val mtu: Int,
    val privateDnsMode: String,
    val gatewayLatencyMs: Int?,
    val ispRawIpLatencyMs: Int?,
    val dnsResolveLatencyMs: Int?,
    val verdict: String,
    val dnsRace: List<DnsRaceEntry>
)

data class CodecDrmProfile(
    val widevineLevel: String,
    val widevineVendor: String,
    val hasHardwareAv1: Boolean,
    val hasHardwareHevc10Bit: Boolean,
    val hasHardwareVp9: Boolean,
    val hardwareVideoDecoderCount: Int
)

data class MemorySweepReport(
    val sweptAppCount: Int,
    val skippedWhitelistedCount: Int = 0,
    val reclaimedRamBytes: Long,
    val availableBeforeBytes: Long,
    val availableAfterBytes: Long,
    val usedShizukuShell: Boolean = false
) {
    fun summaryLabel(): String {
        val freedMb = reclaimedRamBytes.toDouble() / (1024.0 * 1024.0)
        val wlSuffix = if (skippedWhitelistedCount > 0) " ($skippedWhitelistedCount protected)" else ""
        val modePrefix = if (usedShizukuShell) "[Shizuku] " else ""
        return if (freedMb >= 1.0) {
            String.format(Locale.US, "%sFreed %.1f MB across %d apps%s", modePrefix, freedMb, sweptAppCount, wlSuffix)
        } else {
            "${modePrefix}Swept $sweptAppCount cached apps$wlSuffix • Memory optimal"
        }
    }
}

object TelemetryFormatters {
    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
            safe >= mb -> String.format(Locale.US, "%.0f MB", safe / mb)
            safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
            else -> "$safe B"
        }
    }

    fun formatSpeedBytesPerSec(bytesPerSec: Long): String {
        val safe = bytesPerSec.coerceAtLeast(0L)
        val kb = 1024.0
        val mb = kb * 1024.0
        return when {
            safe >= mb -> String.format(Locale.US, "%.1f MB/s", safe / mb)
            safe >= kb -> String.format(Locale.US, "%.0f KB/s", safe / kb)
            else -> "$safe B/s"
        }
    }

    fun formatWatts(watts: Float, isCharging: Boolean): String {
        val mag = abs(watts)
        if (mag < 0.05f) return if (isCharging) "Trickle / Full" else "0.0 W"
        val sign = if (isCharging) "+" else "-"
        return String.format(Locale.US, "%s%.1f W", sign, mag)
    }

    fun formatTemperature(tempCelsius: Float, useFahrenheit: Boolean): String {
        return if (useFahrenheit) {
            val f = (tempCelsius * 9f / 5f) + 32f
            String.format(Locale.US, "%.1f°F", f)
        } else {
            String.format(Locale.US, "%.1f°C", tempCelsius)
        }
    }

    fun normalizeBatteryCurrentToMilliamps(rawCurrentNow: Long): Float {
        val absVal = abs(rawCurrentNow)
        return if (absVal > 15_000L) {
            rawCurrentNow / 1000f
        } else {
            rawCurrentNow.toFloat()
        }
    }

    fun computeBatteryWatts(currentMa: Float, voltageMv: Int): Float {
        if (voltageMv <= 0) return 0f
        return (abs(currentMa) / 1000f) * (voltageMv / 1000f)
    }

    fun formatUptime(uptimeMillis: Long): String {
        val totalSeconds = (uptimeMillis.coerceAtLeast(0L)) / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        return when {
            days > 0L -> "${days}d ${hours}h ${minutes}m"
            hours > 0L -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
