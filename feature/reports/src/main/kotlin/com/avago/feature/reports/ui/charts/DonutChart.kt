package com.avago.feature.reports.ui.charts

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val DONUT_COLORS = listOf(
    Color(0xFF1C8EF0),
    Color(0xFF4CAF50),
    Color(0xFFFFC107),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFFFF5722),
    Color(0xFF607D8B),
)

/**
 * Donut chart for service mix / category breakdowns.
 * [data] maps label → numeric value.
 */
@Composable
fun DonutChart(
    data: Map<String, Double>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return

    val total = data.values.sum().coerceAtLeast(1.0)
    val entries = data.entries.toList()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 40f
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(diameter, diameter)

                var startAngle = -90f
                entries.forEachIndexed { i, (_, value) ->
                    val sweep = (value / total * 360f).toFloat()
                    drawArc(
                        color = DONUT_COLORS[i % DONUT_COLORS.size],
                        startAngle = startAngle,
                        sweepAngle = sweep - 1f, // 1° gap between slices
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Legend
        entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                pair.forEachIndexed { i, (label, value) ->
                    val colorIndex = entries.indexOf(pair[i])
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(DONUT_COLORS[colorIndex % DONUT_COLORS.size])
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${label.take(16)} ${(value / total * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
