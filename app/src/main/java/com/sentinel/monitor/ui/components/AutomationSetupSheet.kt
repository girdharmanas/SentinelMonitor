package com.sentinel.monitor.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.service.AutomationMode
import com.sentinel.monitor.service.AutomationProgress
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh

@Composable
fun AutomationBannerCard(
    progress: AutomationProgress,
    onCancel: () -> Unit,
    onDismissSummary: () -> Unit
) {
    if (!progress.isRunning && progress.lastSummaryBanner == null) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SentinelSurfaceHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (progress.isRunning) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val actionTitle = if (progress.mode == AutomationMode.FORCE_STOP_PROCESSES) {
                        "Stopping Non-Essential (${progress.currentIndex}/${progress.totalCount})"
                    } else {
                        "Clearing App Caches (${progress.currentIndex}/${progress.totalCount})"
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = actionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = SentinelCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current: ${progress.currentAppLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val frac = if (progress.totalCount > 0) {
                    progress.currentIndex.toFloat() / progress.totalCount.toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    color = SentinelCyan,
                    trackColor = SentinelSurface,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
        } else if (progress.lastSummaryBanner != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = progress.lastSummaryBanner,
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinelEmerald,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissSummary, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSetupBottomSheet(
    requestedActionTitle: String,
    showSystemCacheFallback: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOwnAppInfo: () -> Unit,
    onUseSystemClearCacheDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SentinelSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = SentinelEmerald)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Enable 1-Tap $requestedActionTitle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Non-root Android restricts direct Force Stop and per-app Cache deletion to System Settings. Enabling 'Sentinel Process & Cache Automation' lets Sentinel tap Force Stop or Clear Cache for you automatically in seconds and return straight here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    onDismiss()
                    onOpenAccessibilitySettings()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable in Accessibility Settings")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    text = "Seeing 'App access was denied' / 'Restricted setting'? Tap Enable above once, then open App Info below → tap ⋮ (top-right) → Allow restricted settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SentinelAmber
                )
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenOwnAppInfo()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open App Info (⋮ → Allow restricted settings)")
                }
            }

            if (showSystemCacheFallback) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onUseSystemClearCacheDialog()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Android System Clear-All-Cache Dialog")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
