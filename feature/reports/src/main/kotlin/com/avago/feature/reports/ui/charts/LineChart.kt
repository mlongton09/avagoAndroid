package com.avago.feature.reports.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Simple line chart. [points] is a list of (epochMs, value) pairs sorted by time.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
) {
    if (points.size < 2) return

    val minX = points.minOf { it.first }.toDouble()
    val maxX = points.maxOf { it.first }.toDouble()
    val minY = points.minOf { it.second }.coerceAtMost(0.0)
    val maxY = points.maxOf { it.second }.coerceAtLeast(1.0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val xRange = maxX - minX
        fun toX(epoch: Long) =
            if (xRange == 0.0) size.width / 2f
            else ((epoch - minX) / xRange * size.width).toFloat()

        fun toY(v: Double) =
            (size.height - ((v - minY) / (maxY - minY) * size.height)).toFloat()

        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { i, (epoch, value) ->
            val x = toX(epoch)
            val y = toY(value)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(toX(points.last().first), size.height)
        fillPath.close()

        drawPath(fillPath, color = fillColor)
        drawPath(path, color = lineColor, style = Stroke(width = 3f))

        // Draw dots at each point
        points.forEach { (epoch, value) ->
            drawCircle(
                color = lineColor,
                radius = 5f,
                center = Offset(toX(epoch), toY(value)),
            )
        }
    }
}
