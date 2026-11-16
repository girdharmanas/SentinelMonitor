package com.sentinel.monitor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.Toast
import com.sentinel.monitor.MainActivity
import com.sentinel.monitor.R
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.util.SystemTelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SentinelGlanceWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_SWEEP = "com.sentinel.monitor.ACTION_WIDGET_SWEEP"

        fun refreshAllWidgets(context: Context) {
            val appCtx = context.applicationContext
            val awm = AppWidgetManager.getInstance(appCtx)
            val ids = awm.getAppWidgetIds(ComponentName(appCtx, SentinelGlanceWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val views = buildRemoteViews(appCtx)
                ids.forEach { id -> awm.updateAppWidget(id, views) }
            }
        }

        private suspend fun buildRemoteViews(context: Context): RemoteViews {
            val snap = SystemTelemetryEngine.sampleSnapshot(context)
            val views = RemoteViews(context.packageName, R.layout.sentinel_glance_widget)

            val ramPct = (snap.ramUsageFraction * 100).toInt()
            views.setTextViewText(R.id.widget_ram_val, "$ramPct% Used")
            views.setTextViewText(R.id.widget_ram_sub, "${TelemetryFormatters.formatBytes(snap.availableRamBytes)} free")

            val storagePct = (snap.storageUsageFraction * 100).toInt()
            views.setTextViewText(R.id.widget_storage_val, "${TelemetryFormatters.formatBytes(snap.freeStorageBytes)} Free")
            views.setTextViewText(R.id.widget_storage_sub, "$storagePct% occupied")

            views.setTextViewText(
                R.id.widget_power_val,
                String.format(Locale.US, "%d%% • %.1f°C", snap.batteryPercent, snap.batteryTempCelsius)
            )
            views.setTextViewText(
                R.id.widget_power_sub,
                TelemetryFormatters.formatWatts(snap.batteryPowerWatts, snap.isCharging)
            )

            fun tabPendingIntent(reqCode: Int, tab: String): PendingIntent {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("sentinel://monitor?tab=$tab"),
                    context,
                    MainActivity::class.java
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                return PendingIntent.getActivity(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            views.setOnClickPendingIntent(R.id.widget_root, tabPendingIntent(901, "overview"))
            views.setOnClickPendingIntent(R.id.widget_ram_col, tabPendingIntent(902, "processes"))
            views.setOnClickPendingIntent(R.id.widget_storage_col, tabPendingIntent(903, "storage"))
            views.setOnClickPendingIntent(R.id.widget_power_col, tabPendingIntent(904, "watchdog"))

            val sweepIntent = Intent(context, SentinelGlanceWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_SWEEP
            }
            val sweepPi = PendingIntent.getBroadcast(
                context,
                905,
                sweepIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_sweep_btn, sweepPi)
            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val views = buildRemoteViews(context.applicationContext)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            }
            pending.finish()
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ACTION_WIDGET_SWEEP) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                val appCtx = context.applicationContext
                val report = SystemTelemetryEngine.performSafeMemorySweep(appCtx)
                val views = buildRemoteViews(appCtx)
                val awm = AppWidgetManager.getInstance(appCtx)
                val ids = awm.getAppWidgetIds(ComponentName(appCtx, SentinelGlanceWidgetProvider::class.java))
                ids.forEach { id -> awm.updateAppWidget(id, views) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Sentinel: ${report.summaryLabel()}", Toast.LENGTH_SHORT).show()
                }
                pending.finish()
            }
        }
    }
}
