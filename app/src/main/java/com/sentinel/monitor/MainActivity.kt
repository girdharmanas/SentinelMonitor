package com.sentinel.monitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sentinel.monitor.data.model.SentinelDeepLinkRequest
import com.sentinel.monitor.data.model.SentinelTab
import com.sentinel.monitor.ui.dashboard.SentinelApp
import com.sentinel.monitor.ui.theme.SentinelMonitorTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val deepLinkRequestFlow = MutableStateFlow<SentinelDeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parseIncomingDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            SentinelMonitorTheme {
                SentinelApp(deepLinkRequestFlow = deepLinkRequestFlow)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncomingDeepLink(intent)
    }

    private fun parseIncomingDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "sentinel" && data.host == "monitor") {
            val tabParam = data.getQueryParameter("tab") ?: data.lastPathSegment
            val pkgParam = data.getQueryParameter("pkg")?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedTab = if (pkgParam != null && tabParam == null) {
                SentinelTab.PROCESSES
            } else {
                SentinelTab.fromQueryParam(tabParam)
            }
            deepLinkRequestFlow.value = SentinelDeepLinkRequest(
                tab = resolvedTab,
                inspectPackage = pkgParam
            )
        }
    }
}
