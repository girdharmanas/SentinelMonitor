package com.sentinel.monitor

import com.sentinel.monitor.data.model.MemorySweepReport
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.util.SystemTelemetryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun systemSnapshotAndFormatters_computeFractionsBytesAndUptimeCleanly() {
        val snap = SystemSnapshot(
            totalRamBytes = 8L * 1024L * 1024L * 1024L,
            availableRamBytes = 2L * 1024L * 1024L * 1024L,
            totalStorageBytes = 128L * 1024L * 1024L * 1024L,
            freeStorageBytes = 32L * 1024L * 1024L * 1024L
        )
        assertEquals(0.75f, snap.ramUsageFraction, 0.001f)
        assertEquals(0.75f, snap.storageUsageFraction, 0.001f)
        assertEquals("2.0 GB", TelemetryFormatters.formatBytes(snap.availableRamBytes))
        assertEquals("1d 2h 5m", TelemetryFormatters.formatUptime((86_400L + 7_200L + 300L) * 1000L))
    }

    @Test
    fun safeMemorySweep_protectsSentinelSuiteSystemServicesAndActiveLauncher() {
        val dynamicProtected = setOf("com.custom.home")
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.sentinel.monitor", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.sentinel.launcher", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.sentinel.reader", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.vantage.launcher", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.android.systemui", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.google.android.inputmethod.latin", dynamicProtected))
        assertTrue(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.custom.home", dynamicProtected))
        assertFalse(SystemTelemetryEngine.shouldProtectPackageFromSweep("com.example.heavygame", dynamicProtected))

        val report = MemorySweepReport(
            sweptAppCount = 12,
            reclaimedRamBytes = 156L * 1024L * 1024L,
            availableBeforeBytes = 1024L,
            availableAfterBytes = 156L * 1024L * 1024L + 1024L
        )
        assertTrue(report.summaryLabel().contains("156.0 MB"))
    }
}
