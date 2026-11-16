package com.sentinel.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sentinel.monitor.ui.dashboard.SentinelApp
import com.sentinel.monitor.ui.theme.SentinelMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SentinelMonitorTheme {
                SentinelApp()
            }
        }
    }
}
