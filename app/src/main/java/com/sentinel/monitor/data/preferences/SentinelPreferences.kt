package com.sentinel.monitor.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sentinel.monitor.data.model.SentinelTab
import com.sentinel.monitor.data.model.SensorBlackboxEvent
import com.sentinel.monitor.data.model.VitalsHistoryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.sentinelDataStore: DataStore<Preferences> by preferencesDataStore(name = "sentinel_monitor_prefs")

enum class MonitorThemeMode(val key: String, val label: String, val subtitle: String) {
    OBSIDIAN("obsidian", "Sentinel Obsidian", "Signature deep slate dark mode"),
    AMOLED("amoled", "Pitch-Black AMOLED", "True #000000 zero-power OLED canvas"),
    LIGHT("light", "Crisp Daylight", "High-contrast light telemetry surfaces"),
    SYSTEM("system", "Follow System", "Auto-switch Light & Obsidian with Android");

    companion object {
        fun fromKey(key: String?): MonitorThemeMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OBSIDIAN
    }
}

enum class MonitorAccentPreset(val key: String, val label: String) {
    EMERALD("emerald", "Sentinel Emerald"),
    CYBER_CYAN("cyber_cyan", "Cyber Cyan"),
    ROYAL_INDIGO("royal_indigo", "Launcher Indigo"),
    SANCTUARY_AMBER("sanctuary_amber", "Reader Gold"),
    CRIMSON_OPS("crimson_ops", "Tactical Crimson"),
    MATERIAL_YOU("material_you", "Material You (Wallpaper)");

    companion object {
        fun fromKey(key: String?): MonitorAccentPreset =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: EMERALD
    }
}

data class SentinelSettings(
    val isLoaded: Boolean = false,
    val lastSeenReleaseVersion: String = "",
    val themeMode: MonitorThemeMode = MonitorThemeMode.OBSIDIAN,
    val accentPreset: MonitorAccentPreset = MonitorAccentPreset.EMERALD,
    val pollingIntervalMs: Long = 2000L,
    val useFahrenheit: Boolean = false,
    val defaultStartupTab: SentinelTab = SentinelTab.VITALS,
    val compactProcessRows: Boolean = false,
    val protectedPackages: Set<String> = emptySet(),
    val firewallBlockedPackages: Set<String> = emptySet(),
    val watchdogEnabled: Boolean = true,
    val chargeLimitAlarmEnabled: Boolean = false,
    val chargeLimitPercent: Int = 80,
    val thermalAlertThresholdC: Float = 41.0f,
    val storageAlertFreePercent: Int = 10,
    val drainAlertPctPerHour: Float = 4.5f,
    val vitalsHistory: List<VitalsHistoryPoint> = emptyList(),
    val sensorBlackboxLog: List<SensorBlackboxEvent> = emptyList(),
    val lastWatchdogCheckMs: Long = 0L
)

object SentinelPreferencesRepository {
    private val KEY_LAST_SEEN_RELEASE_VERSION = stringPreferencesKey("last_seen_release_version")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACCENT_PRESET = stringPreferencesKey("accent_preset")
    private val KEY_POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
    private val KEY_USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
    private val KEY_DEFAULT_STARTUP_TAB = stringPreferencesKey("default_startup_tab")
    private val KEY_COMPACT_PROCESS_ROWS = booleanPreferencesKey("compact_process_rows")
    private val KEY_PROTECTED_PACKAGES = stringSetPreferencesKey("protected_whitelist_packages")
    private val KEY_FIREWALL_BLOCKED_PACKAGES = stringSetPreferencesKey("firewall_blocked_packages")
    private val KEY_WATCHDOG_ENABLED = booleanPreferencesKey("watchdog_enabled")
    private val KEY_CHARGE_ALARM_ENABLED = booleanPreferencesKey("charge_limit_alarm_enabled")
    private val KEY_CHARGE_LIMIT_PCT = intPreferencesKey("charge_limit_pct")
    private val KEY_THERMAL_ALERT_C = floatPreferencesKey("thermal_alert_c")
    private val KEY_STORAGE_ALERT_PCT = intPreferencesKey("storage_alert_free_pct")
    private val KEY_DRAIN_ALERT_PCT_HR = floatPreferencesKey("drain_alert_pct_hr")
    private val KEY_VITALS_HISTORY_JSON = stringPreferencesKey("vitals_history_ring_json")
    private val KEY_SENSOR_BLACKBOX_JSON = stringPreferencesKey("sensor_blackbox_ring_json")
    private val KEY_LAST_WATCHDOG_MS = longPreferencesKey("last_watchdog_check_ms")

    fun settingsFlow(context: Context): Flow<SentinelSettings> {
        return context.applicationContext.sentinelDataStore.data.map { prefs ->
            SentinelSettings(
                isLoaded = true,
                lastSeenReleaseVersion = prefs[KEY_LAST_SEEN_RELEASE_VERSION] ?: "",
                themeMode = MonitorThemeMode.fromKey(prefs[KEY_THEME_MODE]),
                accentPreset = MonitorAccentPreset.fromKey(prefs[KEY_ACCENT_PRESET]),
                pollingIntervalMs = (prefs[KEY_POLLING_INTERVAL_MS] ?: 2000L).coerceIn(1000L, 10_000L),
                useFahrenheit = prefs[KEY_USE_FAHRENHEIT] ?: false,
                defaultStartupTab = SentinelTab.fromQueryParam(prefs[KEY_DEFAULT_STARTUP_TAB]),
                compactProcessRows = prefs[KEY_COMPACT_PROCESS_ROWS] ?: false,
                protectedPackages = prefs[KEY_PROTECTED_PACKAGES] ?: emptySet(),
                firewallBlockedPackages = prefs[KEY_FIREWALL_BLOCKED_PACKAGES] ?: emptySet(),
                watchdogEnabled = prefs[KEY_WATCHDOG_ENABLED] ?: true,
                chargeLimitAlarmEnabled = prefs[KEY_CHARGE_ALARM_ENABLED] ?: false,
                chargeLimitPercent = prefs[KEY_CHARGE_LIMIT_PCT] ?: 80,
                thermalAlertThresholdC = prefs[KEY_THERMAL_ALERT_C] ?: 41.0f,
                storageAlertFreePercent = prefs[KEY_STORAGE_ALERT_PCT] ?: 10,
                drainAlertPctPerHour = prefs[KEY_DRAIN_ALERT_PCT_HR] ?: 4.5f,
                vitalsHistory = VitalsHistoryPoint.decodeList(prefs[KEY_VITALS_HISTORY_JSON] ?: ""),
                sensorBlackboxLog = SensorBlackboxEvent.decodeList(prefs[KEY_SENSOR_BLACKBOX_JSON] ?: ""),
                lastWatchdogCheckMs = prefs[KEY_LAST_WATCHDOG_MS] ?: 0L
            )
        }
    }

    suspend fun getSettingsSnapshot(context: Context): SentinelSettings {
        return settingsFlow(context).first()
    }

    suspend fun markReleaseVersionSeen(context: Context, version: String) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_LAST_SEEN_RELEASE_VERSION] = version
        }
    }

    suspend fun setThemeMode(context: Context, mode: MonitorThemeMode) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.key
        }
    }

    suspend fun setAccentPreset(context: Context, preset: MonitorAccentPreset) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_ACCENT_PRESET] = preset.key
        }
    }

    suspend fun setPollingIntervalMs(context: Context, intervalMs: Long) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_POLLING_INTERVAL_MS] = intervalMs.coerceIn(1000L, 10_000L)
        }
    }

    suspend fun setUseFahrenheit(context: Context, fahrenheit: Boolean) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_USE_FAHRENHEIT] = fahrenheit
        }
    }

    suspend fun setDefaultStartupTab(context: Context, tab: SentinelTab) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_STARTUP_TAB] = tab.routeKey
        }
    }

    suspend fun setCompactProcessRows(context: Context, compact: Boolean) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_COMPACT_PROCESS_ROWS] = compact
        }
    }

    suspend fun toggleProtectedPackage(context: Context, packageName: String) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            val current = (prefs[KEY_PROTECTED_PACKAGES] ?: emptySet()).toMutableSet()
            if (!current.remove(packageName)) current.add(packageName)
            prefs[KEY_PROTECTED_PACKAGES] = current
        }
    }

    suspend fun clearProtectedPackages(context: Context) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_PROTECTED_PACKAGES] = emptySet()
        }
    }

    suspend fun toggleFirewallBlockedPackage(context: Context, packageName: String) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            val current = (prefs[KEY_FIREWALL_BLOCKED_PACKAGES] ?: emptySet()).toMutableSet()
            if (!current.remove(packageName)) current.add(packageName)
            prefs[KEY_FIREWALL_BLOCKED_PACKAGES] = current
        }
    }

    suspend fun setWatchdogEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_WATCHDOG_ENABLED] = enabled
        }
    }

    suspend fun setChargeLimitAlarm(context: Context, enabled: Boolean, limitPercent: Int = 80) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_CHARGE_ALARM_ENABLED] = enabled
            prefs[KEY_CHARGE_LIMIT_PCT] = limitPercent.coerceIn(60, 95)
        }
    }

    suspend fun updateWatchdogThresholds(
        context: Context,
        thermalC: Float,
        storageFreePct: Int,
        drainPctPerHr: Float
    ) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_THERMAL_ALERT_C] = thermalC.coerceIn(36f, 48f)
            prefs[KEY_STORAGE_ALERT_PCT] = storageFreePct.coerceIn(3, 25)
            prefs[KEY_DRAIN_ALERT_PCT_HR] = drainPctPerHr.coerceIn(2f, 12f)
        }
    }

    suspend fun appendSensorBlackboxEvent(context: Context, event: SensorBlackboxEvent) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            val current = SensorBlackboxEvent.decodeList(prefs[KEY_SENSOR_BLACKBOX_JSON] ?: "")
            val updated = (current + event).takeLast(60)
            prefs[KEY_SENSOR_BLACKBOX_JSON] = SensorBlackboxEvent.encodeList(updated, maxEvents = 60)
        }
    }

    suspend fun clearSensorBlackboxLog(context: Context) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_SENSOR_BLACKBOX_JSON] = "[]"
        }
    }

    suspend fun clearVitalsHistory(context: Context) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_VITALS_HISTORY_JSON] = "[]"
        }
    }

    suspend fun appendVitalsHistoryPoint(
        context: Context,
        point: VitalsHistoryPoint,
        minIntervalMs: Long = 120_000L,
        markWatchdogCheck: Boolean = false
    ) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            val current = VitalsHistoryPoint.decodeList(prefs[KEY_VITALS_HISTORY_JSON] ?: "")
            val lastTs = current.lastOrNull()?.timestampMs ?: 0L
            if (point.timestampMs - lastTs >= minIntervalMs || current.isEmpty()) {
                val updated = (current + point).takeLast(96)
                prefs[KEY_VITALS_HISTORY_JSON] = VitalsHistoryPoint.encodeList(updated, maxPoints = 96)
            }
            if (markWatchdogCheck) {
                prefs[KEY_LAST_WATCHDOG_MS] = point.timestampMs
            }
        }
    }

    internal fun serializeBackupJson(settings: SentinelSettings): String {
        return JSONObject().apply {
            put("schema", "sentinel_monitor_backup_v1")
            put("exportedAtMs", System.currentTimeMillis())
            put("themeMode", settings.themeMode.key)
            put("accentPreset", settings.accentPreset.key)
            put("pollingIntervalMs", settings.pollingIntervalMs)
            put("useFahrenheit", settings.useFahrenheit)
            put("defaultStartupTab", settings.defaultStartupTab.routeKey)
            put("compactProcessRows", settings.compactProcessRows)
            put("watchdogEnabled", settings.watchdogEnabled)
            put("chargeLimitAlarmEnabled", settings.chargeLimitAlarmEnabled)
            put("chargeLimitPercent", settings.chargeLimitPercent)
            put("thermalAlertThresholdC", settings.thermalAlertThresholdC.toDouble())
            put("storageAlertFreePercent", settings.storageAlertFreePercent)
            put("drainAlertPctPerHour", settings.drainAlertPctPerHour.toDouble())
            put("protectedPackages", JSONArray(settings.protectedPackages.sorted()))
            put("firewallBlockedPackages", JSONArray(settings.firewallBlockedPackages.sorted()))
        }.toString(2)
    }

    internal fun parseBackupJson(rawJson: String): SentinelSettings {
        val obj = JSONObject(rawJson)
        fun readSet(key: String): Set<String> {
            val arr = obj.optJSONArray(key) ?: return emptySet()
            return buildSet {
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }
        return SentinelSettings(
            isLoaded = true,
            themeMode = MonitorThemeMode.fromKey(obj.optString("themeMode", "obsidian")),
            accentPreset = MonitorAccentPreset.fromKey(obj.optString("accentPreset", "emerald")),
            pollingIntervalMs = obj.optLong("pollingIntervalMs", 2000L).coerceIn(1000L, 10_000L),
            useFahrenheit = obj.optBoolean("useFahrenheit", false),
            defaultStartupTab = SentinelTab.fromQueryParam(obj.optString("defaultStartupTab", "overview")),
            compactProcessRows = obj.optBoolean("compactProcessRows", false),
            watchdogEnabled = obj.optBoolean("watchdogEnabled", true),
            chargeLimitAlarmEnabled = obj.optBoolean("chargeLimitAlarmEnabled", false),
            chargeLimitPercent = obj.optInt("chargeLimitPercent", 80).coerceIn(60, 95),
            thermalAlertThresholdC = obj.optDouble("thermalAlertThresholdC", 41.0).toFloat().coerceIn(36f, 48f),
            storageAlertFreePercent = obj.optInt("storageAlertFreePercent", 10).coerceIn(3, 25),
            drainAlertPctPerHour = obj.optDouble("drainAlertPctPerHour", 4.5).toFloat().coerceIn(2f, 12f),
            protectedPackages = readSet("protectedPackages"),
            firewallBlockedPackages = readSet("firewallBlockedPackages")
        )
    }

    suspend fun importBackupJson(context: Context, rawJson: String): Result<SentinelSettings> {
        return runCatching {
            val imported = parseBackupJson(rawJson)
            context.applicationContext.sentinelDataStore.edit { prefs ->
                prefs[KEY_THEME_MODE] = imported.themeMode.key
                prefs[KEY_ACCENT_PRESET] = imported.accentPreset.key
                prefs[KEY_POLLING_INTERVAL_MS] = imported.pollingIntervalMs
                prefs[KEY_USE_FAHRENHEIT] = imported.useFahrenheit
                prefs[KEY_DEFAULT_STARTUP_TAB] = imported.defaultStartupTab.routeKey
                prefs[KEY_COMPACT_PROCESS_ROWS] = imported.compactProcessRows
                prefs[KEY_WATCHDOG_ENABLED] = imported.watchdogEnabled
                prefs[KEY_CHARGE_ALARM_ENABLED] = imported.chargeLimitAlarmEnabled
                prefs[KEY_CHARGE_LIMIT_PCT] = imported.chargeLimitPercent
                prefs[KEY_THERMAL_ALERT_C] = imported.thermalAlertThresholdC
                prefs[KEY_STORAGE_ALERT_PCT] = imported.storageAlertFreePercent
                prefs[KEY_DRAIN_ALERT_PCT_HR] = imported.drainAlertPctPerHour
                prefs[KEY_PROTECTED_PACKAGES] = imported.protectedPackages
                prefs[KEY_FIREWALL_BLOCKED_PACKAGES] = imported.firewallBlockedPackages
            }
            imported
        }
    }
}
