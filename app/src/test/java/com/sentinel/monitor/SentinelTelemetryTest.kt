package com.sentinel.monitor

import com.sentinel.monitor.data.model.CpuCoreSample
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.MemorySweepReport
import com.sentinel.monitor.data.model.SensorBlackboxEvent
import com.sentinel.monitor.data.model.SentinelReleaseNotes
import com.sentinel.monitor.data.model.SentinelTab
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.data.model.VitalsHistoryPoint
import com.sentinel.monitor.data.preferences.MonitorAccentPreset
import com.sentinel.monitor.data.preferences.MonitorThemeMode
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.data.preferences.SentinelSettings
import com.sentinel.monitor.service.SentinelAutomationService
import com.sentinel.monitor.util.AppInspectorEngine
import com.sentinel.monitor.util.NetworkAndHardwareLabEngine
import com.sentinel.monitor.util.ShizukuShellBridge
import com.sentinel.monitor.util.StorageScavengerEngine
import com.sentinel.monitor.util.SystemTelemetryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SentinelTelemetryTest {

    @Test
    fun telemetryRag_classifiesUsageAndThermalThresholdsDeterministically() {
        assertEquals(TelemetryRag.HEALTHY, TelemetryRag.fromUsageFraction(0.45f))
        assertEquals(TelemetryRag.ELEVATED, TelemetryRag.fromUsageFraction(0.72f))
        assertEquals(TelemetryRag.CRITICAL, TelemetryRag.fromUsageFraction(0.91f))

        assertEquals(TelemetryRag.HEALTHY, TelemetryRag.fromBatteryTempCelsius(33.5f))
        assertEquals(TelemetryRag.ELEVATED, TelemetryRag.fromBatteryTempCelsius(38.0f))
        assertEquals(TelemetryRag.CRITICAL, TelemetryRag.fromBatteryTempCelsius(43.2f))
    }

    @Test
    fun procMemInfoWattageCpuAndFormatters_computeAccurately() {
        val memLines = listOf(
            "MemTotal:        8123456 kB",
            "MemAvailable:    3100000 kB",
            "Cached:          1850000 kB",
            "SwapTotal:       4194304 kB",
            "SwapFree:        2097152 kB"
        )
        val parsed = SystemTelemetryEngine.parseProcMemInfo(memLines)
        assertEquals(4194304L * 1024L, parsed["SwapTotal"])
        assertEquals(2097152L * 1024L, parsed["SwapFree"])
        assertEquals(1850000L * 1024L, parsed["Cached"])

        // µA vs mA normalization & Wattage computation
        val maFromMicro = TelemetryFormatters.normalizeBatteryCurrentToMilliamps(-1_250_000L)
        assertEquals(-1250f, maFromMicro, 0.1f)
        val maDirect = TelemetryFormatters.normalizeBatteryCurrentToMilliamps(-650L)
        assertEquals(-650f, maDirect, 0.1f)
        val watts = TelemetryFormatters.computeBatteryWatts(maFromMicro, 4000)
        assertEquals(5.0f, watts, 0.01f)
        assertEquals("-5.0 W", TelemetryFormatters.formatWatts(watts, isCharging = false))
        assertEquals("+5.0 W", TelemetryFormatters.formatWatts(watts, isCharging = true))

        // CPU core utilization & Peak MHz
        val cores = listOf(
            CpuCoreSample(0, curFreqKhz = 1_200_000L, minFreqKhz = 300_000L, maxFreqKhz = 2_100_000L),
            CpuCoreSample(7, curFreqKhz = 3_100_000L, minFreqKhz = 600_000L, maxFreqKhz = 3_100_000L)
        )
        val snap = SystemSnapshot(
            totalRamBytes = 8L * 1024L * 1024L * 1024L,
            availableRamBytes = 2L * 1024L * 1024L * 1024L,
            swapTotalBytes = parsed["SwapTotal"]!!,
            swapFreeBytes = parsed["SwapFree"]!!,
            totalStorageBytes = 128L * 1024L * 1024L * 1024L,
            freeStorageBytes = 32L * 1024L * 1024L * 1024L,
            cpuCores = cores
        )
        assertEquals(0.75f, snap.ramUsageFraction, 0.001f)
        assertEquals(0.50f, snap.swapUsageFraction, 0.001f)
        assertEquals(3100, snap.peakCpuMhz)
        assertEquals(0.75f, snap.avgCpuUtilizationFraction, 0.01f)
        assertEquals("2.5 MB/s", TelemetryFormatters.formatSpeedBytesPerSec((2.5 * 1024 * 1024).toLong()))
        assertEquals("1d 2h 5m", TelemetryFormatters.formatUptime((86_400L + 7_200L + 300L) * 1000L))
    }

    @Test
    fun vitalsHistoryDeepLinksAndSecurityHelpers_roundTripDeterministically() {
        // 1. Vitals ring buffer JSON round-trip & 96-point cap
        val points = (1..110).map { i ->
            VitalsHistoryPoint(
                timestampMs = i * 60_000L,
                ramFraction = 0.62f,
                batteryPercent = 85,
                tempCelsius = 33.4f,
                powerWatts = -2.4f,
                cpuFraction = 0.35f
            )
        }
        val encoded = VitalsHistoryPoint.encodeList(points, maxPoints = 96)
        val decoded = VitalsHistoryPoint.decodeList(encoded)
        assertEquals(96, decoded.size)
        assertEquals(110 * 60_000L, decoded.last().timestampMs)
        assertEquals(33.4f, decoded.last().tempCelsius, 0.01f)

        // 2. Deep-link query routing
        assertEquals(SentinelTab.PROCESSES, SentinelTab.fromQueryParam("ram"))
        assertEquals(SentinelTab.PROCESSES, SentinelTab.fromQueryParam("processes"))
        assertEquals(SentinelTab.STORAGE, SentinelTab.fromQueryParam("storage"))
        assertEquals(SentinelTab.SECURITY, SentinelTab.fromQueryParam("security"))
        assertEquals(SentinelTab.WATCHDOG, SentinelTab.fromQueryParam("watchdog"))
        assertEquals(SentinelTab.VITALS, SentinelTab.fromQueryParam(null))

        // 3. Accessibility / NotificationListener Secure setting parser & SHA digest formatter
        val parsedComponents = AppInspectorEngine.parseComponentPackageSet(
            "com.sentinel.launcher/.service.VantageAccessibilityService:com.example.tool/.Svc"
        )
        assertTrue("com.sentinel.launcher" in parsedComponents)
        assertTrue("com.example.tool" in parsedComponents)
        assertEquals(59, AppInspectorEngine.formatDigestHex("sentinel".toByteArray(), "SHA-1").length)

        // 4. Legacy targetSdk detector
        val legacyEntry = InstalledProcessEntry(
            packageName = "com.old.abandonware",
            appLabel = "Old App",
            targetSdk = 28,
            isSystemApp = false,
            isRecentlyActive = false,
            lastTimeUsedMillis = 0L,
            totalForegroundMillis24h = 0L
        )
        assertTrue(legacyEntry.isLegacyTargetSdk)
    }

    @Test
    fun safeMemorySweep_protectsSentinelSuiteSystemServicesAndWhitelist() {
        val dynamicProtected = setOf("com.custom.home", "com.spotify.music")
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.sentinel.monitor", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.sentinel.launcher", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.sentinel.reader", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.android.systemui", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.spotify.music", dynamicProtected))
        assertFalse(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.example.heavygame", dynamicProtected))

        val report = MemorySweepReport(
            sweptAppCount = 12,
            skippedWhitelistedCount = 2,
            reclaimedRamBytes = 156L * 1024L * 1024L,
            availableBeforeBytes = 1024L,
            availableAfterBytes = 156L * 1024L * 1024L + 1024L
        )
        assertTrue(report.summaryLabel().contains("156.0 MB"))
        assertTrue(report.summaryLabel().contains("2 protected"))
    }

    @Test
    fun automationSafetyMatchersAndTargetSelectors_neverTouchUserDataOrProtectedApps() {
        // 1. Ironclad Clear-Cache matcher: accepts Clear Cache, hard-blocks Clear Storage / Clear Data!
        assertTrue(SentinelAutomationService.isSafeClearCacheLabel("Clear cache"))
        assertTrue(SentinelAutomationService.isSafeClearCacheLabel("CLEAR CACHE"))
        assertTrue(SentinelAutomationService.isSafeClearCacheLabel("Clear app cache"))
        assertFalse(SentinelAutomationService.isSafeClearCacheLabel("Clear storage"))
        assertFalse(SentinelAutomationService.isSafeClearCacheLabel("Clear data"))
        assertFalse(SentinelAutomationService.isSafeClearCacheLabel("Clear all data"))
        assertFalse(SentinelAutomationService.isSafeClearCacheLabel("Manage space"))
        assertFalse(SentinelAutomationService.isSafeClearCacheLabel("Uninstall"))

        // 2. Storage subpage row matcher (supports Compose merged multi-line rows)
        assertTrue(SentinelAutomationService.isStorageSectionLabel("Storage & cache"))
        assertTrue(SentinelAutomationService.isStorageSectionLabel("Storage & cache\n312 MB used in internal storage"))
        assertTrue(SentinelAutomationService.isStorageSectionLabel("Storage\n128 MB used"))
        assertFalse(SentinelAutomationService.isStorageSectionLabel("Clear storage"))

        // 3. Force Stop & Confirm dialog matchers
        assertTrue(SentinelAutomationService.isForceStopButtonLabel("Force stop"))
        assertTrue(SentinelAutomationService.isForceStopConfirmButtonLabel("OK"))
        assertTrue(SentinelAutomationService.isForceStopConfirmButtonLabel("Force stop"))
        assertFalse(SentinelAutomationService.isForceStopConfirmButtonLabel("Cancel"))

        // 4. Target selectors exclude stopped apps, protected suite apps, system apps, and whitelist
        val sampleApps = listOf(
            InstalledProcessEntry(
                packageName = "com.example.hog1",
                appLabel = "Hog One",
                isSystemApp = false,
                isStopped = false,
                isRecentlyActive = true,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis24h = 5000L,
                cacheBytes = 85L * 1024 * 1024
            ),
            InstalledProcessEntry(
                packageName = "com.example.alreadystopped",
                appLabel = "Already Stopped",
                isSystemApp = false,
                isStopped = true,
                isRecentlyActive = false,
                lastTimeUsedMillis = 0L,
                totalForegroundMillis24h = 0L,
                cacheBytes = 12L * 1024 * 1024
            ),
            InstalledProcessEntry(
                packageName = "com.spotify.music",
                appLabel = "Spotify",
                isSystemApp = false,
                isStopped = false,
                isRecentlyActive = true,
                lastTimeUsedMillis = 1000L,
                totalForegroundMillis24h = 9000L,
                cacheBytes = 240L * 1024 * 1024
            ),
            InstalledProcessEntry(
                packageName = "com.sentinel.launcher",
                appLabel = "Sentinel Launcher",
                isSystemApp = false,
                isStopped = false,
                isRecentlyActive = true,
                lastTimeUsedMillis = 2000L,
                totalForegroundMillis24h = 60000L,
                cacheBytes = 2L * 1024 * 1024
            )
        )

        val stopTargets = AppInspectorEngine.selectNonEssentialRunningTargets(
            processes = sampleApps,
            protectedPackages = setOf("com.spotify.music"),
            ownPackage = "com.sentinel.monitor"
        )
        assertEquals(listOf("com.example.hog1"), stopTargets.map { it.packageName })

        val cacheTargets = AppInspectorEngine.selectCacheHogTargets(
            processes = sampleApps,
            ownPackage = "com.sentinel.monitor"
        )
        assertEquals("com.spotify.music", cacheTargets.first().packageName)
    }

    @Test
    fun v2PowerSuiteEngines_deepSleepTrackersDuplicatesCableDnsAndShellGuard_workDeterministically() {
        // 1. Kernel Deep-Sleep % calculation
        val sleepSnap = SystemSnapshot(
            uptimeMillis = 100_000L,
            awakeMillis = 24_000L
        )
        assertEquals(76_000L, sleepSnap.deepSleepMillis)
        assertEquals(76, sleepSnap.deepSleepPercent)

        // 2. Sensor Blackbox JSON ring buffer & 60-event cap
        val events = (1..75).map { idx ->
            SensorBlackboxEvent(
                timestampMs = idx * 1000L,
                sensorType = if (idx % 2 == 0) "CAMERA" else "MIC",
                durationMillis = 2400L
            )
        }
        val decodedEvents = SensorBlackboxEvent.decodeList(SensorBlackboxEvent.encodeList(events, maxEvents = 60))
        assertEquals(60, decodedEvents.size)
        assertEquals(75_000L, decodedEvents.last().timestampMs)
        assertEquals("MIC", decodedEvents.last().sensorType)

        // 3. Embedded Tracker & Ad-SDK signature scanner
        val trackers = AppInspectorEngine.detectEmbeddedTrackers(
            listOf(
                "com.example.app.MainActivity",
                "com.google.android.gms.ads.AdActivity",
                "com.google.firebase.analytics.connector.internal.AnalyticsConnectorRegistrar",
                "com.facebook.appevents.AppEventsLogger",
                "io.sentry.android.core.SentryInitProvider"
            )
        )
        assertEquals(
            listOf("Google AdMob", "Firebase Analytics", "Meta AppEvents", "Sentry Telemetry"),
            trackers
        )
        assertTrue(AppInspectorEngine.detectEmbeddedTrackers(listOf("com.sentinel.monitor.MainActivity")).isEmpty())

        // 4. Storage Scavenger exact SHA-256 duplicate finder (retaining oldest original, ignoring same-size non-matches)
        val tmpDir = Files.createTempDirectory("sentinel_scavenger_test").toFile()
        try {
            val orig = File(tmpDir, "doc_v1.pdf").apply {
                writeBytes("identical-payload-content-sentinel".toByteArray())
                setLastModified(1_000_000L)
            }
            val copy = File(tmpDir, "doc_v1 (1).pdf").apply {
                writeBytes("identical-payload-content-sentinel".toByteArray())
                setLastModified(2_000_000L)
            }
            val diffSameLen = File(tmpDir, "other_same_len.pdf").apply {
                writeBytes("different-payload-content-sentinel".toByteArray())
                setLastModified(3_000_000L)
            }
            val dupes = StorageScavengerEngine.findDuplicateFilesBySizeAndHash(listOf(copy, orig, diffSameLen))
            assertEquals(1, dupes.size)
            assertEquals(copy.absolutePath, dupes.first().first.absolutePath)
            assertEquals(orig.absolutePath, dupes.first().second.absolutePath)
        } finally {
            tmpDir.deleteRecursively()
        }

        // 5. USB-C Cable Grader & 3-Hop Network Fault Isolator + RFC 1035 DNS packet
        val pdReport = NetworkAndHardwareLabEngine.gradeCableSamples(
            isCharging = true,
            voltagesMv = listOf(8950, 9010, 8980),
            currentsMa = listOf(2800f, 2900f, 2850f),
            wattsSamples = listOf(25.0f, 26.1f, 25.6f)
        )
        assertTrue(pdReport.gradeTitle.startsWith("Grade S"))
        assertEquals(60, pdReport.voltageSwingMv)

        val sagReport = NetworkAndHardwareLabEngine.gradeCableSamples(
            isCharging = true,
            voltagesMv = listOf(5100, 4620, 4700),
            currentsMa = listOf(1500f, 1200f, 1300f),
            wattsSamples = listOf(7.6f, 5.5f, 6.1f)
        )
        assertTrue(sagReport.gradeTitle.startsWith("Grade D"))

        assertTrue(
            NetworkAndHardwareLabEngine.classifyHopVerdict(
                isOnline = true,
                gatewayMs = 4,
                ispMs = 14,
                dnsMs = null,
                fastestDnsLabel = null
            ).startsWith("DNS Resolver Fault")
        )
        assertTrue(
            NetworkAndHardwareLabEngine.classifyHopVerdict(
                isOnline = true,
                gatewayMs = 3,
                ispMs = null,
                dnsMs = null,
                fastestDnsLabel = null
            ).startsWith("ISP / WAN Outage")
        )

        val dnsPacket = NetworkAndHardwareLabEngine.buildDnsQueryPacket("example.com", txId = 0x534D)
        assertEquals(29, dnsPacket.size)
        assertEquals(0x53.toByte(), dnsPacket[0])
        assertEquals(0x4D.toByte(), dnsPacket[1])

        // 6. Shizuku shell identifier sanitization & new v2 deep-link routes
        assertTrue(ShizukuShellBridge.isValidPackageIdentifier("com.sentinel.launcher"))
        assertFalse(ShizukuShellBridge.isValidPackageIdentifier("com.bad; rm -rf /"))
        assertEquals(SentinelTab.PROCESSES, SentinelTab.fromQueryParam("xray"))
        assertEquals(SentinelTab.STORAGE, SentinelTab.fromQueryParam("scavenger"))
        assertEquals(SentinelTab.SECURITY, SentinelTab.fromQueryParam("firewall"))
        assertEquals(SentinelTab.WATCHDOG, SentinelTab.fromQueryParam("dns"))

        // 7. Sentinel Release Notes integrity check
        val releases = SentinelReleaseNotes.RELEASES
        assertTrue(releases.size >= 4)
        assertEquals(SentinelReleaseNotes.CURRENT_VERSION, releases.first().version)
        assertTrue(releases.first().isLatest)
        assertEquals(releases.size, releases.map { it.version }.toSet().size)
        assertTrue(releases.all { r -> r.highlights.isNotEmpty() && r.highlights.all { h -> h.title.isNotBlank() && h.description.isNotBlank() } })
    }

    @Test
    fun settingsThemesUnitsAndBackupJson_roundTripLosslessly() {
        assertEquals("30.0°C", TelemetryFormatters.formatTemperature(30.0f, useFahrenheit = false))
        assertEquals("86.0°F", TelemetryFormatters.formatTemperature(30.0f, useFahrenheit = true))

        assertEquals(MonitorThemeMode.AMOLED, MonitorThemeMode.fromKey("amoled"))
        assertEquals(MonitorThemeMode.LIGHT, MonitorThemeMode.fromKey("LIGHT"))
        assertEquals(MonitorThemeMode.OBSIDIAN, MonitorThemeMode.fromKey("unknown_theme"))
        assertEquals(MonitorAccentPreset.MATERIAL_YOU, MonitorAccentPreset.fromKey("material_you"))
        assertEquals(MonitorAccentPreset.ROYAL_INDIGO, MonitorAccentPreset.fromKey("royal_indigo"))

        val original = SentinelSettings(
            themeMode = MonitorThemeMode.AMOLED,
            accentPreset = MonitorAccentPreset.CYBER_CYAN,
            pollingIntervalMs = 1000L,
            useFahrenheit = true,
            defaultStartupTab = SentinelTab.WATCHDOG,
            compactProcessRows = true,
            watchdogEnabled = false,
            chargeLimitAlarmEnabled = true,
            chargeLimitPercent = 85,
            thermalAlertThresholdC = 43.5f,
            storageAlertFreePercent = 12,
            drainAlertPctPerHour = 5.5f,
            protectedPackages = setOf("com.sentinel.launcher", "com.spotify.music"),
            firewallBlockedPackages = setOf("com.example.adgame")
        )

        val json = SentinelPreferencesRepository.serializeBackupJson(original)
        val restored = SentinelPreferencesRepository.parseBackupJson(json)
        assertEquals(MonitorThemeMode.AMOLED, restored.themeMode)
        assertEquals(MonitorAccentPreset.CYBER_CYAN, restored.accentPreset)
        assertEquals(1000L, restored.pollingIntervalMs)
        assertTrue(restored.useFahrenheit)
        assertEquals(SentinelTab.WATCHDOG, restored.defaultStartupTab)
        assertTrue(restored.compactProcessRows)
        assertFalse(restored.watchdogEnabled)
        assertTrue(restored.chargeLimitAlarmEnabled)
        assertEquals(85, restored.chargeLimitPercent)
        assertEquals(43.5f, restored.thermalAlertThresholdC, 0.01f)
        assertEquals(12, restored.storageAlertFreePercent)
        assertEquals(setOf("com.sentinel.launcher", "com.spotify.music"), restored.protectedPackages)
        assertEquals(setOf("com.example.adgame"), restored.firewallBlockedPackages)
    }
}
