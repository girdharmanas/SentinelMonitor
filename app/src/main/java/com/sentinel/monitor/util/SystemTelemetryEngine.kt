package com.sentinel.monitor.util

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.sentinel.monitor.data.model.CpuCoreSample
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.MemorySweepReport
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

object SystemTelemetryEngine {

    private val CORE_PROTECTED_PACKAGES = setOf(
        "com.sentinel.monitor",
        "com.sentinel.launcher",
        "com.sentinel.reader",
        "com.vantage.launcher",
        "com.reflow.reader",
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.deskclock",
        "com.android.deskclock"
    )

    @Volatile
    private var prevTrafficSampleAtMs: Long = 0L

    @Volatile
    private var prevTotalRxBytes: Long = -1L

    @Volatile
    private var prevTotalTxBytes: Long = -1L

    fun shouldProtectPackageFromSweep(
        packageName: String,
        dynamicProtectedPackages: Set<String> = emptySet()
    ): Boolean {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed in CORE_PROTECTED_PACKAGES || trimmed in dynamicProtectedPackages) return true
        return trimmed.startsWith("com.android.providers.") ||
            trimmed.contains("inputmethod", ignoreCase = true) ||
            trimmed.contains("dialer", ignoreCase = true) ||
            trimmed.contains("telecom", ignoreCase = true) ||
            trimmed.contains("clock", ignoreCase = true)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    internal fun parseProcMemInfo(lines: Iterable<String>): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            val rest = line.substring(colon + 1).trim()
            val numStr = rest.takeWhile { it.isDigit() }
            val kb = numStr.toLongOrNull() ?: continue
            out[key] = kb * 1024L
        }
        return out
    }

    private fun readCpuCoreSamples(coreCount: Int): List<CpuCoreSample> {
        return (0 until coreCount).map { idx ->
            val dir = "/sys/devices/system/cpu/cpu$idx/cpufreq"
            val cur = readSysfsLong("$dir/scaling_cur_freq")
                ?: readSysfsLong("$dir/cpuinfo_cur_freq")
                ?: 0L
            val min = readSysfsLong("$dir/cpuinfo_min_freq")
                ?: readSysfsLong("$dir/scaling_min_freq")
                ?: 300_000L
            val max = readSysfsLong("$dir/cpuinfo_max_freq")
                ?: readSysfsLong("$dir/scaling_max_freq")
                ?: 2_400_000L
            val gov = readSysfsString("$dir/scaling_governor") ?: "schedutil"
            CpuCoreSample(
                coreIndex = idx,
                curFreqKhz = cur,
                minFreqKhz = min,
                maxFreqKhz = max,
                governor = gov
            )
        }
    }

    private fun readSysfsLong(path: String): Long? = runCatching {
        val f = File(path)
        if (f.canRead()) f.readText().trim().toLongOrNull() else null
    }.getOrNull()

    private fun readSysfsString(path: String): String? = runCatching {
        val f = File(path)
        if (f.canRead()) f.readText().trim().takeIf { it.isNotEmpty() } else null
    }.getOrNull()

    suspend fun sampleSnapshot(context: Context): SystemSnapshot = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val activityManager = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val procMem = runCatching {
            val f = File("/proc/meminfo")
            if (f.canRead()) parseProcMemInfo(f.readLines()) else emptyMap()
        }.getOrDefault(emptyMap())

        val cachedRam = procMem["Cached"] ?: 0L
        val swapTotal = procMem["SwapTotal"] ?: 0L
        val swapFree = procMem["SwapFree"] ?: 0L

        val (totalStorage, freeStorage) = runCatching {
            val statFs = StatFs(Environment.getDataDirectory().absolutePath)
            statFs.totalBytes to statFs.availableBytes
        }.getOrDefault(0L to 0L)

        // Battery, Current (mA), Voltage (mV), Wattage (W), & Cycle Count
        val batteryIntent = runCatching {
            appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 100
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280) ?: 280
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
            ?.let { if (it in 1..20) it * 1000 else it } ?: 4000
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        } else {
            -1
        }

        val batteryManager = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawCurrent = runCatching {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        }.getOrDefault(0L)
        val currentMa = TelemetryFormatters.normalizeBatteryCurrentToMilliamps(rawCurrent)
        val powerWatts = TelemetryFormatters.computeBatteryWatts(currentMa, voltageMv)

        // PowerManager Thermal Throttling Status
        val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val (thermalLabel, thermalLevel) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val st = runCatching { powerManager.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
            val lbl = when (st) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal (Unthrottled)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Thermal Limit"
                PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "Emergency Thermal"
                else -> "Nominal"
            }
            lbl to st
        } else {
            "Nominal" to 0
        }

        // Per-core CPU clocks
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuCores = readCpuCoreSamples(coreCount)
        val primaryGovernor = cpuCores.firstOrNull()?.governor ?: "schedutil"

        // Network throughput & Wi-Fi link telemetry
        val nowElapsed = SystemClock.elapsedRealtime()
        val curRx = TrafficStats.getTotalRxBytes()
        val curTx = TrafficStats.getTotalTxBytes()
        var rxPerSec = 0L
        var txPerSec = 0L
        val prevAt = prevTrafficSampleAtMs
        if (prevAt > 0L && curRx >= 0L && curTx >= 0L && prevTotalRxBytes >= 0L && nowElapsed > prevAt) {
            val dtSec = ((nowElapsed - prevAt) / 1000.0).coerceAtLeast(0.25)
            rxPerSec = ((curRx - prevTotalRxBytes).coerceAtLeast(0L) / dtSec).toLong()
            txPerSec = ((curTx - prevTotalTxBytes).coerceAtLeast(0L) / dtSec).toLong()
        }
        prevTrafficSampleAtMs = nowElapsed
        prevTotalRxBytes = curRx
        prevTotalTxBytes = curTx

        val (transportLabel, wifiDetail, vpnActive) = sampleNetworkLinkDetails(appCtx)

        // Display Refresh Rate & SoC
        val displayHz = runCatching {
            val dm = appCtx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60
        }.getOrDefault(60)

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }

        val isShizukuInstalled = runCatching {
            appCtx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        }.getOrDefault(false)

        SystemSnapshot(
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            cachedRamBytes = cachedRam,
            swapTotalBytes = swapTotal,
            swapFreeBytes = swapFree,
            isLowMemory = memInfo.lowMemory,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            batteryPercent = batteryPct.coerceIn(0, 100),
            batteryTempCelsius = tempTenths / 10f,
            batteryVoltageMv = voltageMv,
            batteryCurrentMa = currentMa,
            batteryPowerWatts = powerWatts,
            batteryCycleCount = cycleCount,
            isCharging = isCharging,
            thermalStatusLabel = thermalLabel,
            thermalSeverityLevel = thermalLevel,
            cpuCoreCount = coreCount,
            cpuCores = cpuCores,
            cpuGovernor = primaryGovernor,
            netRxBytesPerSec = rxPerSec,
            netTxBytesPerSec = txPerSec,
            netTransportLabel = transportLabel,
            wifiBandOrLinkLabel = wifiDetail,
            isVpnActive = vpnActive,
            displayRefreshHz = displayHz,
            socModel = socModel,
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim(),
            androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            uptimeMillis = SystemClock.elapsedRealtime(),
            hasUsageStatsAccess = hasUsageStatsPermission(appCtx),
            hasOverlayPermission = Settings.canDrawOverlays(appCtx),
            isShizukuInstalled = isShizukuInstalled,
            sampledAtMillis = System.currentTimeMillis()
        )
    }

    private fun sampleNetworkLinkDetails(context: Context): Triple<String, String, Boolean> {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Triple("Offline", "", false)
            val active = cm.activeNetwork ?: return Triple("Offline", "", false)
            val caps = cm.getNetworkCapabilities(active) ?: return Triple("Offline", "", false)
            val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        caps.transportInfo as? WifiInfo
                    } else {
                        null
                    } ?: run {
                        @Suppress("DEPRECATION")
                        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
                    }
                    val detail = if (wifiInfo != null && wifiInfo.linkSpeed > 0) {
                        val freqMhz = wifiInfo.frequency
                        val band = when {
                            freqMhz >= 5925 -> "6 GHz"
                            freqMhz >= 4900 -> "5 GHz"
                            freqMhz >= 2400 -> "2.4 GHz"
                            else -> ""
                        }
                        val rssi = wifiInfo.rssi
                        listOfNotNull(
                            band.takeIf { it.isNotEmpty() },
                            "${wifiInfo.linkSpeed} Mbps",
                            if (rssi in -120..-1) "$rssi dBm" else null
                        ).joinToString(" • ")
                    } else {
                        val downMbps = (caps.linkDownstreamBandwidthKbps / 1000).coerceAtLeast(1)
                        "~$downMbps Mbps"
                    }
                    Triple("Wi-Fi", detail, isVpn)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val metered = if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "Metered" else "Unmetered"
                    Triple("Cellular", metered, isVpn)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Triple("Ethernet", "Wired", isVpn)
                else -> Triple("Connected", "", isVpn)
            }
        }.getOrDefault(Triple("Offline", "", false))
    }

    suspend fun queryProcessCatalog(context: Context): List<InstalledProcessEntry> {
        return AppInspectorEngine.queryFullAppCatalog(context)
    }

    suspend fun performSafeMemorySweep(context: Context): MemorySweepReport = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val activityManager = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val beforeMem = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        val userProtected = runCatching {
            SentinelPreferencesRepository.getSettingsSnapshot(appCtx).protectedPackages
        }.getOrDefault(emptySet())
        val dynamicProtected = buildDynamicProtectedSet(appCtx) + userProtected

        val pm = appCtx.packageManager
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        var sweptCount = 0
        var skippedWhitelist = 0
        for (app in installed) {
            val pkg = app.packageName ?: continue
            val isStopped = (app.flags and ApplicationInfo.FLAG_STOPPED) != 0
            if (isStopped) continue
            if (pkg in userProtected) {
                skippedWhitelist++
                continue
            }
            if (shouldProtectPackageFromSweep(pkg, dynamicProtected)) continue
            runCatching {
                activityManager.killBackgroundProcesses(pkg)
                sweptCount++
            }
        }

        Runtime.getRuntime().gc()
        delay(220L)

        val afterMem = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val reclaimed = (afterMem.availMem - beforeMem.availMem).coerceAtLeast(0L)
        MemorySweepReport(
            sweptAppCount = sweptCount,
            skippedWhitelistedCount = skippedWhitelist,
            reclaimedRamBytes = reclaimed,
            availableBeforeBytes = beforeMem.availMem,
            availableAfterBytes = afterMem.availMem
        )
    }

    private fun buildDynamicProtectedSet(context: Context): Set<String> {
        val result = mutableSetOf(context.packageName)
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching {
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { result.add(it) }
        }
        runCatching {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            defaultIme?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        }
        return result
    }
}
