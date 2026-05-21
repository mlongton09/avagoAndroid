package com.avago.feature.reports.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Simple bar chart rendered on Canvas.
 * [data] maps category label → value. Bars are auto-scaled to the max value.
 */
@Composable
fun BarChart(
    data: Map<String, Double>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.isEmpty()) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val entries = data.entries.toList()
    val maxValue = entries.maxOf { it.value }.coerceAtLeast(1.0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val labelAreaH = 36f
        val chartH = size.height - labelAreaH
        val barWidth = size.width / entries.size * 0.6f
        val spacing = size.width / entries.size

        entries.forEachIndexed { index, entry ->
            val barH = ((entry.value / maxValue) * chartH).toFloat()
            val x = index * spacing + spacing * 0.2f
            val y = chartH - barH

            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
            )

            // Label
            drawContext.canvas.nativeCanvas.drawText(
                entry.key.take(8),
                x + barWidth / 2,
                size.height - 4f,
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                },
            )
        }
    }
}
