package com.sentinel.monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sentinel.monitor.util.SystemTelemetryEngine
import com.sentinel.monitor.widget.SentinelGlanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Suite Bridge Receiver: triggered by Sentinel Launcher when Focus Space activates
 * (`com.sentinel.monitor.ACTION_FOCUS_BOOST_SWEEP`) to perform a safe background memory sweep.
 */
class SentinelFocusSweepReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FOCUS_BOOST_SWEEP = "com.sentinel.monitor.ACTION_FOCUS_BOOST_SWEEP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FOCUS_BOOST_SWEEP) return
        val pendingResult = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SystemTelemetryEngine.performSafeMemorySweep(appCtx)
                SentinelGlanceWidgetProvider.refreshAllWidgets(appCtx)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
