package com.sentinel.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.data.model.SentinelReleaseNotes
import com.sentinel.monitor.ui.theme.SentinelAmber
import com.sentinel.monitor.ui.theme.SentinelCyan
import com.sentinel.monitor.ui.theme.SentinelEmerald
import com.sentinel.monitor.ui.theme.SentinelObsidian
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val releases = SentinelReleaseNotes.RELEASES
    var selectedVersion by remember { mutableStateOf(releases.first().version) }
    val activeRelease = remember(selectedVersion) {
        releases.firstOrNull { it.version == selectedVersion } ?: releases.first()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SentinelObsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                SentinelMonitorCrestBadge(modifier = Modifier.size(38.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = SentinelEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "What's New in Sentinel Monitor",
                            style = MaterialTheme.typography.labelLarge,
                            color = SentinelEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${activeRelease.version} • Released ${activeRelease.releaseDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Version Picker Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                releases.forEach { rel ->
                    val label = if (rel.isLatest) "${rel.version} • Latest" else rel.version
                    FilterChip(
                        selected = rel.version == activeRelease.version,
                        onClick = { selectedVersion = rel.version },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            // Release Tagline Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SentinelSurfaceHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = activeRelease.tagline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SentinelCyan,
                    modifier = Modifier.padding(14.dp)
                )
            }

            // Highlights List
            activeRelease.highlights.forEach { item ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SentinelSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SentinelAmber.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = SentinelAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SentinelEmerald),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    }
}
