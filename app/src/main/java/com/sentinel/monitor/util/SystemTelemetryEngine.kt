package com.sentinel.monitor.util

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.MemorySweepReport
import com.sentinel.monitor.data.model.SystemSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun sampleSnapshot(context: Context): SystemSnapshot = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val totalStorage = statFs.totalBytes
        val freeStorage = statFs.availableBytes

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 100
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 280) ?: 280
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        SystemSnapshot(
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            isLowMemory = memInfo.lowMemory,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            batteryPercent = batteryPct.coerceIn(0, 100),
            batteryTempCelsius = tempTenths / 10f,
            isCharging = isCharging,
            cpuCoreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim(),
            androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            uptimeMillis = SystemClock.elapsedRealtime(),
            hasUsageStatsAccess = hasUsageStatsPermission(context),
            sampledAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun queryProcessCatalog(context: Context): List<InstalledProcessEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val usageByPkg = if (hasUsageStatsPermission(context)) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            usm?.queryAndAggregateUsageStats(now - 86_400_000L, now).orEmpty()
        } else {
            emptyMap()
        }

        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        apps.mapNotNull { appInfo ->
            val pkg = appInfo.packageName ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(pkg)
            val usage = usageByPkg[pkg]
            val lastUsed = usage?.lastTimeUsed ?: 0L
            val fgTime = usage?.totalTimeInForeground ?: 0L
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            InstalledProcessEntry(
                packageName = pkg,
                appLabel = label,
                isSystemApp = isSystem,
                isRecentlyActive = !isStopped && (lastUsed > now - 3_600_000L || !isSystem),
                lastTimeUsedMillis = lastUsed,
                totalForegroundMillis24h = fgTime
            )
        }.sortedWith(
            compareByDescending<InstalledProcessEntry> { it.totalForegroundMillis24h }
                .thenByDescending { it.lastTimeUsedMillis }
                .thenBy { it.isSystemApp }
                .thenBy { it.appLabel.lowercase() }
        )
    }

    suspend fun performSafeMemorySweep(context: Context): MemorySweepReport = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val beforeMem = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        val dynamicProtected = buildDynamicProtectedSet(context)
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        var sweptCount = 0
        for (app in installed) {
            val pkg = app.packageName ?: continue
            val isStopped = (app.flags and ApplicationInfo.FLAG_STOPPED) != 0
            if (isStopped || shouldProtectPackageFromSweep(pkg, dynamicProtected)) continue
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
