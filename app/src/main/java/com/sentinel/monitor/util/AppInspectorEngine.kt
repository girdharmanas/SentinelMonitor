package com.sentinel.monitor.util

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import com.sentinel.monitor.data.model.AppXRayDetails
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.ProcessExitSummary
import com.sentinel.monitor.data.model.SensitivePermBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object AppInspectorEngine {

    suspend fun queryFullAppCatalog(context: Context): List<InstalledProcessEntry> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val now = System.currentTimeMillis()
        val hasUsage = SystemTelemetryEngine.hasUsageStatsPermission(appCtx)

        val usm = appCtx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val usageByPkg = if (hasUsage && usm != null) {
            runCatching { usm.queryAndAggregateUsageStats(now - 86_400_000L, now) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val storageStatsManager = if (hasUsage) {
            appCtx.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else {
            null
        }
        val userHandle = Process.myUserHandle()

        val enabledAccessibilityPkgs = parseComponentPackageSet(
            runCatching {
                Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull()
        )
        val enabledNotifListenerPkgs = parseComponentPackageSet(
            runCatching {
                Settings.Secure.getString(appCtx.contentResolver, "enabled_notification_listeners")
            }.getOrNull()
        )

        val pkgInfos: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val appOps = appCtx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val bootAutoStartPkgs = queryBootCompletedPackages(pm)
        val (wifiByUid, mobileByUid) = if (hasUsage) {
            queryNetworkBytesByUid24h(appCtx, now - 86_400_000L, now)
        } else {
            emptyMap<Int, Long>() to emptyMap<Int, Long>()
        }

        pkgInfos.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            val pkg = pkgInfo.packageName ?: return@mapNotNull null
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && !isUpdatedSystem

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            val usage = usageByPkg[pkg]
            val lastUsed = usage?.lastTimeUsed ?: 0L
            val fgTime = usage?.totalTimeInForeground ?: 0L
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            var appBytes = 0L
            var dataBytes = 0L
            var cacheBytes = 0L
            if (storageStatsManager != null) {
                runCatching {
                    val st = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, userHandle)
                    appBytes = st.appBytes
                    dataBytes = st.dataBytes
                    cacheBytes = st.cacheBytes
                }.onFailure {
                    appBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0L)
                }
            } else {
                appBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0L)
            }

            val (installerLabel, isSideloaded) = classifyInstallerSource(pm, pkg, isSystem || isUpdatedSystem)
            val standbyLabel = when {
                fgTime > 15 * 60_000L || (lastUsed > now - 30 * 60_000L) -> "ACTIVE"
                fgTime > 0L || (lastUsed > now - 6 * 3_600_000L) -> "WORKING_SET"
                lastUsed > now - 24 * 3_600_000L -> "FREQUENT"
                isStopped -> "STOPPED"
                else -> "RARE"
            }

            val badges = evaluateSensitivePermissions(
                pkg = pkg,
                uid = appInfo.uid,
                pkgInfo = pkgInfo,
                appOps = appOps,
                enabledAccessibility = enabledAccessibilityPkgs,
                enabledNotifListeners = enabledNotifListenerPkgs
            )

            val ignoringDoze = !isSystem && runCatching {
                powerManager?.isIgnoringBatteryOptimizations(pkg) == true
            }.getOrDefault(false)

            InstalledProcessEntry(
                packageName = pkg,
                appLabel = label,
                versionName = pkgInfo.versionName ?: "",
                minSdk = appInfo.minSdkVersion,
                targetSdk = appInfo.targetSdkVersion,
                isSystemApp = isSystem,
                isUpdatedSystemApp = isUpdatedSystem,
                isStopped = isStopped,
                isRecentlyActive = !isStopped && (lastUsed > now - 3_600_000L || fgTime > 0L),
                lastTimeUsedMillis = lastUsed,
                totalForegroundMillis24h = fgTime,
                standbyBucketLabel = standbyLabel,
                appBytes = appBytes,
                dataBytes = dataBytes,
                cacheBytes = cacheBytes,
                wifiDataBytes24h = wifiByUid[appInfo.uid] ?: 0L,
                mobileDataBytes24h = mobileByUid[appInfo.uid] ?: 0L,
                isIgnoringBatteryOptimizations = ignoringDoze,
                isBootAutoStart = pkg in bootAutoStartPkgs,
                installerSourceLabel = installerLabel,
                isSideloaded = isSideloaded,
                sensitivePermissions = badges
            )
        }.sortedWith(
            compareByDescending<InstalledProcessEntry> { it.totalForegroundMillis24h }
                .thenByDescending { it.cacheBytes }
                .thenByDescending { it.lastTimeUsedMillis }
                .thenBy { it.isSystemApp }
                .thenBy { it.appLabel.lowercase() }
        )
    }

    internal fun parseComponentPackageSet(settingValue: String?): Set<String> {
        if (settingValue.isNullOrBlank()) return emptySet()
        return settingValue.split(':')
            .mapNotNull { token -> token.substringBefore('/').trim().takeIf { it.isNotEmpty() } }
            .toSet()
    }

    fun selectNonEssentialRunningTargets(
        processes: List<InstalledProcessEntry>,
        protectedPackages: Set<String>,
        ownPackage: String = "com.sentinel.monitor"
    ): List<InstalledProcessEntry> {
        val dynamicProtected = protectedPackages + ownPackage
        return processes.filter { entry ->
            !entry.isSystemApp &&
                !entry.isStopped &&
                entry.packageName != ownPackage &&
                !SystemTelemetryEngine.shouldProtectPackageFromSweep(entry.packageName, dynamicProtected)
        }
    }

    fun selectCacheHogTargets(
        processes: List<InstalledProcessEntry>,
        ownPackage: String = "com.sentinel.monitor",
        minCacheBytes: Long = 512 * 1024L,
        maxTargets: Int = 15
    ): List<InstalledProcessEntry> {
        return processes.filter { entry ->
            entry.packageName != ownPackage &&
                entry.cacheBytes >= minCacheBytes &&
                (!entry.isSystemApp || entry.isUpdatedSystemApp)
        }.sortedByDescending { it.cacheBytes }
            .take(maxTargets)
    }

    fun canUseSystemClearAllCacheDialog(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val intent = android.content.Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        return runCatching {
            intent.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
    }

    private fun classifyInstallerSource(
        pm: PackageManager,
        pkg: String,
        isSystemOrUpdated: Boolean
    ): Pair<String, Boolean> {
        val installerPkg = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
        }.getOrNull()

        return when {
            installerPkg == "com.android.vending" -> "Google Play" to false
            installerPkg == "com.sec.android.app.samsungapps" -> "Galaxy Store" to false
            installerPkg?.contains("packageinstaller", ignoreCase = true) == true -> "Sideloaded APK" to !isSystemOrUpdated
            installerPkg == null && isSystemOrUpdated -> "System ROM" to false
            installerPkg == null -> "ADB / Sideloaded" to true
            else -> installerPkg.substringAfterLast('.') to !isSystemOrUpdated
        }
    }

    private fun evaluateSensitivePermissions(
        pkg: String,
        uid: Int,
        pkgInfo: PackageInfo,
        appOps: AppOpsManager?,
        enabledAccessibility: Set<String>,
        enabledNotifListeners: Set<String>
    ): List<SensitivePermBadge> {
        val badges = ArrayList<SensitivePermBadge>(4)
        if (pkg in enabledAccessibility) badges.add(SensitivePermBadge.ACCESSIBILITY)
        if (pkg in enabledNotifListeners) badges.add(SensitivePermBadge.NOTIFICATION_LISTENER)

        val reqPerms = pkgInfo.requestedPermissions.orEmpty()
        val reqFlags = pkgInfo.requestedPermissionsFlags

        fun isRuntimePermGranted(perm: String): Boolean {
            val idx = reqPerms.indexOf(perm)
            if (idx < 0 || reqFlags == null || idx >= reqFlags.size) return false
            return (reqFlags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }

        fun checkAppOpAllowed(opStr: String): Boolean {
            if (appOps == null) return false
            return runCatching {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(opStr, uid, pkg)
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(opStr, uid, pkg)
                }
                mode == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
        }

        if (Manifest.permission.REQUEST_INSTALL_PACKAGES in reqPerms &&
            checkAppOpAllowed("android:request_install_packages")
        ) {
            badges.add(SensitivePermBadge.INSTALL_PACKAGES)
        }
        if (Manifest.permission.SYSTEM_ALERT_WINDOW in reqPerms &&
            checkAppOpAllowed(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)
        ) {
            badges.add(SensitivePermBadge.OVERLAY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Manifest.permission.MANAGE_EXTERNAL_STORAGE in reqPerms &&
            checkAppOpAllowed("android:manage_external_storage")
        ) {
            badges.add(SensitivePermBadge.ALL_FILES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            isRuntimePermGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            badges.add(SensitivePermBadge.BACKGROUND_LOCATION)
        }
        if (isRuntimePermGranted(Manifest.permission.CAMERA)) {
            badges.add(SensitivePermBadge.CAMERA)
        }
        if (isRuntimePermGranted(Manifest.permission.RECORD_AUDIO)) {
            badges.add(SensitivePermBadge.MICROPHONE)
        }
        return badges
    }

    private fun queryBootCompletedPackages(pm: PackageManager): Set<String> {
        return runCatching {
            val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
            val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryBroadcastReceivers(bootIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryBroadcastReceivers(bootIntent, 0)
            }
            receivers.mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    @Suppress("DEPRECATION")
    private fun queryNetworkBytesByUid24h(
        context: Context,
        startMs: Long,
        endMs: Long
    ): Pair<Map<Int, Long>, Map<Int, Long>> {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyMap<Int, Long>() to emptyMap<Int, Long>()

        fun aggregateTransport(netType: Int): Map<Int, Long> {
            val map = HashMap<Int, Long>()
            runCatching {
                val stats = nsm.querySummary(netType, null, startMs, endMs) ?: return@runCatching
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val total = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                        if (total > 0L) {
                            map[bucket.uid] = (map[bucket.uid] ?: 0L) + total
                        }
                    }
                } finally {
                    runCatching { stats.close() }
                }
            }
            return map
        }

        return aggregateTransport(ConnectivityManager.TYPE_WIFI) to
            aggregateTransport(ConnectivityManager.TYPE_MOBILE)
    }

    private val TRACKER_SDK_SIGNATURES: List<Pair<String, List<String>>> = listOf(
        "Google AdMob" to listOf("com.google.android.gms.ads."),
        "Firebase Analytics" to listOf("com.google.android.gms.measurement.", "com.google.firebase.analytics."),
        "Meta AppEvents" to listOf("com.facebook.appevents.", "com.facebook.ads.", "com.facebook.CustomTab"),
        "Unity Ads" to listOf("com.unity3d.services.ads.", "com.unity3d.ads."),
        "AppLovin MAX" to listOf("com.applovin."),
        "Adjust Attribution" to listOf("com.adjust.sdk."),
        "AppsFlyer" to listOf("com.appsflyer."),
        "Branch.io" to listOf("io.branch."),
        "ironSource" to listOf("com.ironsource."),
        "Vungle / Liftoff" to listOf("com.vungle."),
        "InMobi" to listOf("com.inmobi."),
        "Chartboost" to listOf("com.chartboost."),
        "OneSignal Push" to listOf("com.onesignal."),
        "Braze" to listOf("com.braze.", "com.appboy."),
        "Amplitude" to listOf("com.amplitude."),
        "Mixpanel" to listOf("com.mixpanel."),
        "Segment" to listOf("com.segment.analytics."),
        "Sentry Telemetry" to listOf("io.sentry.")
    )

    internal fun detectEmbeddedTrackers(componentClassNames: Iterable<String>): List<String> {
        val found = LinkedHashSet<String>()
        for (cls in componentClassNames) {
            for ((sdkName, prefixes) in TRACKER_SDK_SIGNATURES) {
                if (prefixes.any { pfx -> cls.startsWith(pfx, ignoreCase = true) }) {
                    found.add(sdkName)
                }
            }
        }
        return found.toList()
    }

    suspend fun inspectAppXRay(context: Context, entry: InstalledProcessEntry): AppXRayDetails =
        withContext(Dispatchers.IO) {
            val pm = context.applicationContext.packageManager
            val certFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val compFlags = certFlags or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS
            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(entry.packageName, PackageManager.PackageInfoFlags.of(compFlags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(entry.packageName, compFlags)
                }
            }.getOrNull() ?: runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(entry.packageName, PackageManager.PackageInfoFlags.of(certFlags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(entry.packageName, certFlags)
                }
            }.getOrNull()

            val componentNames = buildList {
                pkgInfo?.activities?.forEach { it.name?.let(::add) }
                pkgInfo?.services?.forEach { it.name?.let(::add) }
                pkgInfo?.receivers?.forEach { it.name?.let(::add) }
                pkgInfo?.providers?.forEach { it.name?.let(::add) }
            }
            val trackers = detectEmbeddedTrackers(componentNames)

            val certBytes = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val si = pkgInfo?.signingInfo
                    (si?.apkContentsSigners ?: si?.signingCertificateHistory)?.firstOrNull()?.toByteArray()
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo?.signatures?.firstOrNull()?.toByteArray()
                }
            }.getOrNull()

            val sha1 = certBytes?.let { formatDigestHex(it, "SHA-1") } ?: "Unavailable"
            val sha256 = certBytes?.let { formatDigestHex(it, "SHA-256") } ?: "Unavailable"
            val srcDir = pkgInfo?.applicationInfo?.sourceDir ?: ""

            val abis = detectApkAbis(srcDir, pkgInfo?.applicationInfo?.splitPublicSourceDirs)
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                (pkgInfo?.versionCode ?: 0).toLong()
            }

            AppXRayDetails(
                entry = entry,
                versionCode = vCode,
                firstInstallTimeMs = pkgInfo?.firstInstallTime ?: 0L,
                lastUpdateTimeMs = pkgInfo?.lastUpdateTime ?: 0L,
                nativeAbis = abis.ifEmpty { listOf("Pure JVM / DEX (Universal)") },
                detectedTrackers = trackers,
                sha1Fingerprint = sha1,
                sha256Fingerprint = sha256,
                sourceApkDir = srcDir
            )
        }

    internal fun formatDigestHex(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString(":") { b -> "%02X".format(b) }
    }

    private fun detectApkAbis(baseApkPath: String, splitPaths: Array<String>?): List<String> {
        val found = LinkedHashSet<String>()
        val candidates = buildList {
            if (baseApkPath.isNotBlank()) add(baseApkPath)
            splitPaths?.forEach { add(it) }
        }
        for (apkPath in candidates) {
            runCatching {
                val f = File(apkPath)
                if (!f.canRead()) return@runCatching
                ZipFile(f).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.startsWith("lib/") && name.endsWith(".so")) {
                            val abi = name.removePrefix("lib/").substringBefore('/')
                            if (abi.isNotBlank()) found.add(abi)
                        }
                    }
                }
            }
        }
        return found.toList()
    }

    suspend fun queryRecentExitReasons(context: Context): List<ProcessExitSummary> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()
            val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return@withContext emptyList()
            runCatching {
                am.getHistoricalProcessExitReasons(null, 0, 25).map { info ->
                    ProcessExitSummary(
                        packageName = info.processName.substringBefore(':'),
                        processName = info.processName,
                        reasonLabel = mapExitReasonCode(info.reason),
                        description = info.description ?: "Process terminated",
                        timestampMs = info.timestamp,
                        pssKb = info.pss
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun mapExitReasonCode(reason: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "EXIT"
        return when (reason) {
            android.app.ApplicationExitInfo.REASON_CRASH -> "JVM CRASH"
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE CRASH"
            android.app.ApplicationExitInfo.REASON_ANR -> "ANR FREEZE"
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW MEMORY OOM"
            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE CPU/BATTERY"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "FORCE STOPPED"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "OS SIGNAL"
            else -> "EXITED ($reason)"
        }
    }
}
