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
import com.sentinel.monitor.data.model.VitalsHistoryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.sentinelDataStore: DataStore<Preferences> by preferencesDataStore(name = "sentinel_monitor_prefs")

data class SentinelSettings(
    val protectedPackages: Set<String> = emptySet(),
    val watchdogEnabled: Boolean = true,
    val thermalAlertThresholdC: Float = 41.0f,
    val storageAlertFreePercent: Int = 10,
    val drainAlertPctPerHour: Float = 4.5f,
    val vitalsHistory: List<VitalsHistoryPoint> = emptyList(),
    val lastWatchdogCheckMs: Long = 0L
)

object SentinelPreferencesRepository {
    private val KEY_PROTECTED_PACKAGES = stringSetPreferencesKey("protected_whitelist_packages")
    private val KEY_WATCHDOG_ENABLED = booleanPreferencesKey("watchdog_enabled")
    private val KEY_THERMAL_ALERT_C = floatPreferencesKey("thermal_alert_c")
    private val KEY_STORAGE_ALERT_PCT = intPreferencesKey("storage_alert_free_pct")
    private val KEY_DRAIN_ALERT_PCT_HR = floatPreferencesKey("drain_alert_pct_hr")
    private val KEY_VITALS_HISTORY_JSON = stringPreferencesKey("vitals_history_ring_json")
    private val KEY_LAST_WATCHDOG_MS = longPreferencesKey("last_watchdog_check_ms")

    fun settingsFlow(context: Context): Flow<SentinelSettings> {
        return context.applicationContext.sentinelDataStore.data.map { prefs ->
            SentinelSettings(
                protectedPackages = prefs[KEY_PROTECTED_PACKAGES] ?: emptySet(),
                watchdogEnabled = prefs[KEY_WATCHDOG_ENABLED] ?: true,
                thermalAlertThresholdC = prefs[KEY_THERMAL_ALERT_C] ?: 41.0f,
                storageAlertFreePercent = prefs[KEY_STORAGE_ALERT_PCT] ?: 10,
                drainAlertPctPerHour = prefs[KEY_DRAIN_ALERT_PCT_HR] ?: 4.5f,
                vitalsHistory = VitalsHistoryPoint.decodeList(prefs[KEY_VITALS_HISTORY_JSON] ?: ""),
                lastWatchdogCheckMs = prefs[KEY_LAST_WATCHDOG_MS] ?: 0L
            )
        }
    }

    suspend fun getSettingsSnapshot(context: Context): SentinelSettings {
        return settingsFlow(context).first()
    }

    suspend fun toggleProtectedPackage(context: Context, packageName: String) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            val current = (prefs[KEY_PROTECTED_PACKAGES] ?: emptySet()).toMutableSet()
            if (!current.remove(packageName)) {
                current.add(packageName)
            }
            prefs[KEY_PROTECTED_PACKAGES] = current
        }
    }

    suspend fun setWatchdogEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.sentinelDataStore.edit { prefs ->
            prefs[KEY_WATCHDOG_ENABLED] = enabled
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
}
