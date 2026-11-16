package com.sentinel.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sentinel.monitor.data.model.TelemetryRag
import com.sentinel.monitor.ui.theme.SentinelSurface
import com.sentinel.monitor.ui.theme.SentinelSurfaceHigh
import com.sentinel.monitor.ui.theme.accentColor

@Composable
fun TelemetrySparkline(
    values: List<Float>,
    color: Color,
    minVal: Float = 0f,
    maxVal: Float = 1f,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
) {
    if (values.size < 2) return
    val span = (maxVal - minVal).takeIf { it > 0.0001f } ?: 1f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 3.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(1f)
        val stepX = w / (values.size - 1).coerceAtLeast(1)

        val strokePath = Path()
        val fillPath = Path()

        values.forEachIndexed { idx, raw ->
            val norm = ((raw - minVal) / span).coerceIn(0f, 1f)
            val x = idx * stepX
            val y = padY + usableH * (1f - norm)
            if (idx == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.0f)),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = strokePath,
            color = color,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun TelemetryGaugeCard(
    title: String,
    icon: ImageVector,
    headline: String,
    subtitle: String,
    progress: Float,
    rag: TelemetryRag,
    sparklineValues: List<Float> = emptyList(),
    extraContent: (@Composable () -> Unit)? = null
) {
    val accent = rag.accentColor()
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SentinelSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = rag.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = accent,
                trackColor = SentinelSurfaceHigh,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            if (sparklineValues.size >= 2) {
                Spacer(Modifier.height(12.dp))
                TelemetrySparkline(values = sparklineValues, color = accent)
            }
            if (extraContent != null) {
                Spacer(Modifier.height(10.dp))
                extraContent()
            }
        }
    }
}

@Composable
fun SentinelMonitorCrestBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.24f)
            cubicTo(w * 0.88f, h * 0.58f, w * 0.74f, h * 0.82f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.26f, h * 0.82f, w * 0.12f, h * 0.58f, w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF132A31), Color(0xFF0B1522)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF34D399), Color(0xFF38BDF8)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Stroke(width = w * 0.075f, join = StrokeJoin.Round)
        )
        val pulse = Path().apply {
            moveTo(w * 0.25f, h * 0.52f)
            lineTo(w * 0.39f, h * 0.52f)
            lineTo(w * 0.46f, h * 0.31f)
            lineTo(w * 0.55f, h * 0.70f)
            lineTo(w * 0.63f, h * 0.43f)
            lineTo(w * 0.69f, h * 0.52f)
            lineTo(w * 0.75f, h * 0.52f)
        }
        drawPath(
            path = pulse,
            color = Color(0xFFA7F3D0),
            style = Stroke(width = w * 0.068f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
