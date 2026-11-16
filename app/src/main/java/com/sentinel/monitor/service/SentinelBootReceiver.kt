package com.sentinel.monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.widget.SentinelGlanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SentinelBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val settings = SentinelPreferencesRepository.getSettingsSnapshot(context)
                    SentinelWatchdogJobService.scheduleOrCancel(context, settings.watchdogEnabled)
                    SentinelGlanceWidgetProvider.refreshAllWidgets(context)
                }
                pending.finish()
            }
        }
    }
}
