package com.sentinel.monitor.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zero-root, zero-CPU Per-App Local Sinkhole Firewall.
 *
 * Uses [VpnService.Builder.addAllowedApplication] exclusively for blocked packages so that
 * 100% of unblocked apps bypass the TUN interface completely at the kernel routing layer
 * (0% CPU overhead, 0 packet-forwarding loops, 0 speed penalty), while blocked apps'
 * IPv4/IPv6 sockets hit an inert sinkhole TUN interface and fail immediately.
 */
class SentinelFirewallVpnService : VpnService() {

    companion object {
        private const val ACTION_APPLY = "com.sentinel.monitor.action.FIREWALL_APPLY"
        private const val ACTION_STOP = "com.sentinel.monitor.action.FIREWALL_STOP"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _activeBlockedCountFlow = MutableStateFlow(0)
        val activeBlockedCountFlow: StateFlow<Int> = _activeBlockedCountFlow.asStateFlow()

        fun startOrReload(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, SentinelFirewallVpnService::class.java).apply {
                action = ACTION_APPLY
            }
            runCatching { appCtx.startService(intent) }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, SentinelFirewallVpnService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { appCtx.startService(intent) }
        }
    }

    private val vpnScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardownTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                vpnScope.launch {
                    applyFirewallRules()
                }
                return START_STICKY
            }
        }
    }

    private suspend fun applyFirewallRules() {
        if (prepare(applicationContext) != null) {
            teardownTunnel()
            stopSelf()
            return
        }

        val blocked = runCatching {
            SentinelPreferencesRepository.getSettingsSnapshot(applicationContext).firewallBlockedPackages
        }.getOrDefault(emptySet()) - packageName

        if (blocked.isEmpty()) {
            teardownTunnel()
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession("Sentinel App Firewall")
            .setMtu(1500)
            .addAddress("10.215.173.1", 30)
            .addAddress("fd00:2:fd00:1::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        var addedCount = 0
        for (pkg in blocked) {
            val ok = runCatching {
                builder.addAllowedApplication(pkg)
                true
            }.getOrDefault(false)
            if (ok) addedCount++
        }

        if (addedCount == 0) {
            teardownTunnel()
            stopSelf()
            return
        }

        val newTun = runCatching { builder.establish() }.getOrNull()
        val oldTun = tunInterface
        tunInterface = newTun
        runCatching { oldTun?.close() }

        if (newTun != null) {
            _activeBlockedCountFlow.value = addedCount
            _isRunningFlow.value = true
        } else {
            _activeBlockedCountFlow.value = 0
            _isRunningFlow.value = false
            stopSelf()
        }
    }

    private fun teardownTunnel() {
        val old = tunInterface
        tunInterface = null
        runCatching { old?.close() }
        _activeBlockedCountFlow.value = 0
        _isRunningFlow.value = false
    }

    override fun onRevoke() {
        teardownTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownTunnel()
        vpnScope.cancel()
        super.onDestroy()
    }
}
