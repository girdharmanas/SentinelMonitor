package com.sentinel.monitor

import android.app.Application
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import com.sentinel.monitor.service.CameraMicBlackboxTracker
import com.sentinel.monitor.service.SentinelWatchdogJobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SentinelApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CameraMicBlackboxTracker.register(this)
        appScope.launch {
            runCatching {
                val settings = SentinelPreferencesRepository.getSettingsSnapshot(this@SentinelApplication)
                SentinelWatchdogJobService.scheduleOrCancel(this@SentinelApplication, settings.watchdogEnabled)
            }
        }
    }
}
