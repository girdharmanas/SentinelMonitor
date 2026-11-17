package com.sentinel.monitor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sentinel.monitor.MainActivity
import com.sentinel.monitor.R
import com.sentinel.monitor.data.model.VitalsHistoryPoint
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.util.SystemTelemetryEngine
import com.sentinel.monitor.widget.SentinelGlanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class SentinelWatchdogJobService : JobService() {

    companion object {
        private const val JOB_ID = 7801
        private const val ALERT_CHANNEL_ID = "sentinel_watchdog_alerts"

        fun scheduleOrCancel(context: Context, enabled: Boolean) {
            val appCtx = context.applicationContext
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return

            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(appCtx, SentinelWatchdogJobService::class.java)
            )
                .setPeriodic(15 * 60 * 1000L) // Android minimum periodic interval (15m)
                .setPersisted(true)
                .build()
            scheduler.schedule(jobInfo)
        }

        suspend fun runAuditNow(context: Context, forceSavePoint: Boolean = false): List<String> {
            val appCtx = context.applicationContext
            val settings = SentinelPreferencesRepository.getSettingsSnapshot(appCtx)
            val snap = SystemTelemetryEngine.sampleSnapshot(appCtx)

            val point = VitalsHistoryPoint(
                timestampMs = snap.sampledAtMillis,
                ramFraction = snap.ramUsageFraction,
                batteryPercent = snap.batteryPercent,
                tempCelsius = snap.batteryTempCelsius,
                powerWatts = if (snap.isCharging) snap.batteryPowerWatts else -snap.batteryPowerWatts,
                cpuFraction = snap.avgCpuUtilizationFraction
            )

            val prevPoint = settings.vitalsHistory.lastOrNull()
            SentinelPreferencesRepository.appendVitalsHistoryPoint(
                context = appCtx,
                point = point,
                minIntervalMs = if (forceSavePoint) 0L else 120_000L,
                markWatchdogCheck = true
            )
            SentinelGlanceWidgetProvider.refreshAllWidgets(appCtx)

            if (!settings.watchdogEnabled) return emptyList()

            val alerts = ArrayList<String>()

            // 1. Thermal threshold check
            if (snap.batteryTempCelsius >= settings.thermalAlertThresholdC) {
                alerts.add(
                    String.format(
                        Locale.US,
                        "High Thermal Alert: %.1f°C (limit %.0f°C)",
                        snap.batteryTempCelsius,
                        settings.thermalAlertThresholdC
                    )
                )
            }

            // 2. Free /data storage threshold check
            val freePct = ((1f - snap.storageUsageFraction) * 100f).toInt()
            if (snap.totalStorageBytes > 0L && freePct <= settings.storageAlertFreePercent) {
                alerts.add("Low Storage Alert: Only $freePct% free on /data partition")
            }

            // 3. Battery Longevity Charge Limit Alarm (e.g. 80% ceiling)
            if (settings.chargeLimitAlarmEnabled &&
                snap.isCharging &&
                snap.batteryPercent >= settings.chargeLimitPercent
            ) {
                alerts.add(
                    "Charge Limit Reached: ${snap.batteryPercent}% (target ${settings.chargeLimitPercent}% — unplug to preserve battery health)"
                )
            }

            // 4. Abnormal battery discharge rate check
            if (!snap.isCharging && prevPoint != null) {
                val dtHours = (point.timestampMs - prevPoint.timestampMs) / 3_600_000.0
                val dropPct = prevPoint.batteryPercent - point.batteryPercent
                if (dtHours >= 0.20 && dropPct >= 2) {
                    val pctPerHr = (dropPct / dtHours).toFloat()
                    if (pctPerHr >= settings.drainAlertPctPerHour) {
                        alerts.add(
                            String.format(
                                Locale.US,
                                "Elevated Drain: %.1f%%/hr battery drop detected",
                                pctPerHr
                            )
                        )
                    }
                }
            }

            if (alerts.isNotEmpty()) {
                postWatchdogNotification(appCtx, alerts)
            }
            return alerts
        }

        private fun postWatchdogNotification(context: Context, alerts: List<String>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Sentinel Watchdog Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Proactive alerts for high temperature, low storage, and abnormal battery drain."
                }
                nm.createNotificationChannel(ch)
            }

            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("sentinel://monitor?tab=processes"),
                context,
                MainActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                context,
                801,
                deepLinkIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_sentinel)
                .setContentTitle("Sentinel Watchdog Alert")
                .setContentText(alerts.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(alerts.joinToString("\n• ", prefix = "• ")))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(7802, notif)
        }
    }

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            runCatching { runAuditNow(applicationContext) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope.cancel()
        return true
    }
}
