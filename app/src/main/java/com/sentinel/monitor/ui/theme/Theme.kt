package com.sentinel.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sentinel.monitor.data.model.TelemetryRag

val SentinelObsidian = Color(0xFF0A0F1A)
val SentinelSurface = Color(0xFF131C2E)
val SentinelSurfaceHigh = Color(0xFF1A263D)
val SentinelEmerald = Color(0xFF10B981)
val SentinelAmber = Color(0xFFF59E0B)
val SentinelCrimson = Color(0xFFEF4444)
val SentinelCyan = Color(0xFF38BDF8)

private val SentinelDarkColorScheme = darkColorScheme(
    primary = SentinelEmerald,
    onPrimary = Color(0xFF042F2E),
    secondary = SentinelCyan,
    background = SentinelObsidian,
    surface = SentinelSurface,
    surfaceVariant = SentinelSurfaceHigh,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8)
)

fun TelemetryRag.accentColor(): Color = when (this) {
    TelemetryRag.HEALTHY -> SentinelEmerald
    TelemetryRag.ELEVATED -> SentinelAmber
    TelemetryRag.CRITICAL -> SentinelCrimson
}

@Composable
fun SentinelMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SentinelDarkColorScheme,
        content = content
    )
}
