package com.sentinel.monitor.data.model

import java.util.Locale

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
                tempC >= 42.0f -> CRITICAL
                tempC >= 37.5f -> ELEVATED
                else -> HEALTHY
            }
        }
    }
}

data class SystemSnapshot(
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val isLowMemory: Boolean = false,
    val totalStorageBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val batteryPercent: Int = 100,
    val batteryTempCelsius: Float = 28.0f,
    val isCharging: Boolean = false,
    val cpuCoreCount: Int = 1,
    val deviceModel: String = "Android Device",
    val androidVersionLabel: String = "Android",
    val primaryAbi: String = "arm64-v8a",
    val uptimeMillis: Long = 0L,
    val hasUsageStatsAccess: Boolean = false,
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

    val usedStorageBytes: Long
        get() = (totalStorageBytes - freeStorageBytes).coerceAtLeast(0L)

    val storageUsageFraction: Float
        get() = if (totalStorageBytes > 0L) {
            (usedStorageBytes.toDouble() / totalStorageBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class InstalledProcessEntry(
    val packageName: String,
    val appLabel: String,
    val isSystemApp: Boolean,
    val isRecentlyActive: Boolean,
    val lastTimeUsedMillis: Long,
    val totalForegroundMillis24h: Long
)

data class MemorySweepReport(
    val sweptAppCount: Int,
    val reclaimedRamBytes: Long,
    val availableBeforeBytes: Long,
    val availableAfterBytes: Long
) {
    fun summaryLabel(): String {
        val freedMb = reclaimedRamBytes.toDouble() / (1024.0 * 1024.0)
        return if (freedMb >= 1.0) {
            String.format(Locale.US, "Freed %.1f MB across %d background apps", freedMb, sweptAppCount)
        } else {
            "Swept $sweptAppCount cached background apps • Memory optimal"
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
