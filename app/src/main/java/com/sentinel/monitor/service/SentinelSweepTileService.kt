package com.sentinel.monitor.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.util.SystemTelemetryEngine
import com.sentinel.monitor.widget.SentinelGlanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SentinelSweepTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState(customSubtitle = null)
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Sweeping…"
        }
        tile.updateTile()

        tileScope.launch {
            val report = SystemTelemetryEngine.performSafeMemorySweep(applicationContext)
            val freeFmt = TelemetryFormatters.formatBytes(report.availableAfterBytes)
            refreshTileState(customSubtitle = "$freeFmt Free")
            SentinelGlanceWidgetProvider.refreshAllWidgets(applicationContext)
            Toast.makeText(
                applicationContext,
                "Sentinel: ${report.summaryLabel()}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshTileState(customSubtitle: String?) {
        tileScope.launch {
            val snap = SystemTelemetryEngine.sampleSnapshot(applicationContext)
            val tile = qsTile ?: return@launch
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Sentinel Sweep"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = customSubtitle ?: "${TelemetryFormatters.formatBytes(snap.availableRamBytes)} Free"
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
