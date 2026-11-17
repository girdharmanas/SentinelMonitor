package com.sentinel.monitor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.data.preferences.MonitorAccentPreset
import com.sentinel.monitor.data.preferences.MonitorThemeMode

data class SentinelPalette(
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val warning: Color,
    val danger: Color
) {
    companion object {
        val DefaultObsidian = SentinelPalette(
            isLight = false,
            background = Color(0xFF0A0F1A),
            surface = Color(0xFF131C2E),
            surfaceHigh = Color(0xFF1A263D),
            onBackground = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF94A3B8),
            primary = Color(0xFF10B981),
            onPrimary = Color(0xFF042F2E),
            secondary = Color(0xFF38BDF8),
            warning = Color(0xFFF59E0B),
            danger = Color(0xFFEF4444)
        )
    }
}

val LocalSentinelPalette = staticCompositionLocalOf { SentinelPalette.DefaultObsidian }

val SentinelObsidian: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.background

val SentinelSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.surface

val SentinelSurfaceHigh: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.surfaceHigh

val SentinelEmerald: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.primary

val SentinelCyan: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.secondary

val SentinelAmber: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.warning

val SentinelCrimson: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSentinelPalette.current.danger

@Composable
@ReadOnlyComposable
fun TelemetryRag.accentColor(): Color = when (this) {
    TelemetryRag.HEALTHY -> LocalSentinelPalette.current.primary
    TelemetryRag.ELEVATED -> LocalSentinelPalette.current.warning
    TelemetryRag.CRITICAL -> LocalSentinelPalette.current.danger
}

fun MonitorAccentPreset.previewSwatchColor(): Color = when (this) {
    MonitorAccentPreset.EMERALD -> Color(0xFF10B981)
    MonitorAccentPreset.CYBER_CYAN -> Color(0xFF06B6D4)
    MonitorAccentPreset.ROYAL_INDIGO -> Color(0xFF6366F1)
    MonitorAccentPreset.SANCTUARY_AMBER -> Color(0xFFF59E0B)
    MonitorAccentPreset.CRIMSON_OPS -> Color(0xFFF43F5E)
    MonitorAccentPreset.MATERIAL_YOU -> Color(0xFF38BDF8)
}

@Composable
fun SentinelMonitorTheme(
    themeMode: MonitorThemeMode = MonitorThemeMode.OBSIDIAN,
    accentPreset: MonitorAccentPreset = MonitorAccentPreset.EMERALD,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useLight = when (themeMode) {
        MonitorThemeMode.LIGHT -> true
        MonitorThemeMode.SYSTEM -> !systemDark
        MonitorThemeMode.OBSIDIAN, MonitorThemeMode.AMOLED -> false
    }
    val useAmoled = themeMode == MonitorThemeMode.AMOLED

    val dynamicScheme = if (accentPreset == MonitorAccentPreset.MATERIAL_YOU &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (useLight) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
    } else {
        null
    }

    val (primary, onPrimary, secondary) = when {
        dynamicScheme != null -> Triple(
            dynamicScheme.primary,
            dynamicScheme.onPrimary,
            dynamicScheme.secondary
        )
        useLight -> when (accentPreset) {
            MonitorAccentPreset.EMERALD, MonitorAccentPreset.MATERIAL_YOU ->
                Triple(Color(0xFF059669), Color.White, Color(0xFF0284C7))
            MonitorAccentPreset.CYBER_CYAN ->
                Triple(Color(0xFF0891B2), Color.White, Color(0xFF4F46E5))
            MonitorAccentPreset.ROYAL_INDIGO ->
                Triple(Color(0xFF4F46E5), Color.White, Color(0xFF0284C7))
            MonitorAccentPreset.SANCTUARY_AMBER ->
                Triple(Color(0xFFD97706), Color.White, Color(0xFF059669))
            MonitorAccentPreset.CRIMSON_OPS ->
                Triple(Color(0xFFE11D48), Color.White, Color(0xFFD97706))
        }
        else -> when (accentPreset) {
            MonitorAccentPreset.EMERALD, MonitorAccentPreset.MATERIAL_YOU ->
                Triple(Color(0xFF10B981), Color(0xFF042F2E), Color(0xFF38BDF8))
            MonitorAccentPreset.CYBER_CYAN ->
                Triple(Color(0xFF22D3EE), Color(0xFF082F49), Color(0xFF818CF8))
            MonitorAccentPreset.ROYAL_INDIGO ->
                Triple(Color(0xFF818CF8), Color(0xFF0F172A), Color(0xFF38BDF8))
            MonitorAccentPreset.SANCTUARY_AMBER ->
                Triple(Color(0xFFFBBF24), Color(0xFF271801), Color(0xFF34D399))
            MonitorAccentPreset.CRIMSON_OPS ->
                Triple(Color(0xFFFB7185), Color(0xFF2B0610), Color(0xFFFBBF24))
        }
    }

    val palette = when {
        useLight -> SentinelPalette(
            isLight = true,
            background = dynamicScheme?.background ?: Color(0xFFF1F5F9),
            surface = dynamicScheme?.surface ?: Color(0xFFFFFFFF),
            surfaceHigh = dynamicScheme?.surfaceVariant ?: Color(0xFFE2E8F0),
            onBackground = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569),
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            warning = Color(0xFFD97706),
            danger = Color(0xFFDC2626)
        )
        useAmoled -> SentinelPalette(
            isLight = false,
            background = Color(0xFF000000),
            surface = Color(0xFF0B0F17),
            surfaceHigh = Color(0xFF141C2B),
            onBackground = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8),
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            warning = Color(0xFFF59E0B),
            danger = Color(0xFFEF4444)
        )
        else -> SentinelPalette(
            isLight = false,
            background = Color(0xFF0A0F1A),
            surface = Color(0xFF131C2E),
            surfaceHigh = Color(0xFF1A263D),
            onBackground = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF94A3B8),
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            warning = Color(0xFFF59E0B),
            danger = Color(0xFFEF4444)
        )
    }

    val colorScheme = if (useLight) {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceHigh,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            onSurfaceVariant = palette.onSurfaceVariant
        )
    } else {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceHigh,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            onSurfaceVariant = palette.onSurfaceVariant
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = useLight
            insets.isAppearanceLightNavigationBars = useLight
        }
    }

    CompositionLocalProvider(LocalSentinelPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
