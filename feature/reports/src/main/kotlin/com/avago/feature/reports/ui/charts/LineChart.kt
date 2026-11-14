package com.avago.feature.reports.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Paint
import com.avago.core.design.chart.ChartFormat
import com.avago.core.design.chart.ChartTheme
import com.avago.core.design.chart.chartTextPaint
import com.avago.core.design.chart.drawChartEmpty
import com.avago.core.design.chart.drawChartGridLine
import com.avago.core.design.chart.drawChartSkeleton
import com.avago.core.design.chart.drawChartZeroLine
import com.avago.feature.reports.R

/**
 * Line chart styled by [ChartTheme] (AvagoWeb docs/chart-style-guide.md § 4):
 * light horizontal gridlines with compact value ticks, emphasised zero
 * baseline, 2dp line without area fill, points hidden except the latest value
 * and [flagged] points (anomalies, drawn larger in `negative`). A `null`
 * value is a gap, never zero.
 *
 * [points] are (epochMs, value) pairs sorted by time. [beginAtZero] may be
 * false only for continuous measures where zero is meaningless (meter
 * readings, fuel economy) — say so in the caller's subtitle.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double?>>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    state: ChartState = ChartState.READY,
    height: Dp = 180.dp,
    beginAtZero: Boolean = true,
    flagged: Set<Int> = emptySet(),
    formatCompact: (Double) -> String = { ChartFormat.numberCompact(it) },
    lineColor: Color? = null,
) {
    val colors = ChartTheme.colors()
    val stroke = lineColor ?: colors.categorical(0)
    val emptyText = stringResource(R.string.reports_empty_state)
    val loadingText = stringResource(R.string.chart_loading)
    val present = points.mapNotNull { (t, v) -> v?.let { t to it } }
    val effectiveState = if (state == ChartState.READY && present.size < 2) ChartState.EMPTY else state
    val description = when (effectiveState) {
        ChartState.LOADING -> loadingText
        ChartState.EMPTY -> emptyText
        ChartState.READY -> contentDescription
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = description },
    ) {
        when (effectiveState) {
            ChartState.LOADING -> { drawChartSkeleton(colors); return@Canvas }
            ChartState.EMPTY -> { drawChartEmpty(emptyText, colors); return@Canvas }
            ChartState.READY -> Unit
        }

        val tickPaint = chartTextPaint(colors.textMuted, align = Paint.Align.RIGHT)
        var minY = present.minOf { it.second }
        var maxY = present.maxOf { it.second }
        if (beginAtZero) { minY = minOf(minY, 0.0); maxY = maxOf(maxY, 0.0) }
        if (maxY == minY) { maxY += 1.0; if (!beginAtZero) minY -= 1.0 }
        val minX = present.minOf { it.first }.toDouble()
        val maxX = present.maxOf { it.first }.toDouble()

        // 4 intervals → 5 ticks (style guide: 4–6 value ticks).
        val ticks = (0..4).map { minY + (maxY - minY) * it / 4.0 }
        val tickTexts = ticks.map(formatCompact)
        val axisW = tickTexts.maxOf { tickPaint.measureText(it) } + 6.dp.toPx()
        val pad = ChartTheme.PointRadius.toPx() * 2
        val plotLeft = axisW + pad
        val plotRight = size.width - pad
        val plotTop = tickPaint.textSize / 2f + pad
        val plotBottom = size.height - tickPaint.textSize / 2f - pad
        val xRange = maxX - minX
        fun toX(epoch: Long) =
            if (xRange == 0.0) (plotLeft + plotRight) / 2f
            else (plotLeft + (epoch - minX) / xRange * (plotRight - plotLeft)).toFloat()
        fun toY(v: Double) = (plotBottom - (v - minY) / (maxY - minY) * (plotBottom - plotTop)).toFloat()

        val native = drawContext.canvas.nativeCanvas
        ticks.forEachIndexed { i, v ->
            val y = toY(v)
            drawChartGridLine(y, plotLeft, plotRight, colors)
            native.drawText(tickTexts[i], axisW - 6.dp.toPx() / 2f, y + tickPaint.textSize / 3f, tickPaint)
        }
        if (minY <= 0.0 && maxY >= 0.0) drawChartZeroLine(toY(0.0), plotLeft, plotRight, colors)

        // Gaps for missing values: a null breaks the path.
        val path = Path()
        var penDown = false
        points.forEach { (epoch, value) ->
            if (value == null) { penDown = false; return@forEach }
            val x = toX(epoch); val y = toY(value)
            if (penDown) path.lineTo(x, y) else path.moveTo(x, y)
            penDown = true
        }
        drawPath(
            path,
            color = stroke,
            style = Stroke(width = ChartTheme.LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        points.forEachIndexed { i, (epoch, value) ->
            if (value != null && i in flagged) {
                drawCircle(colors.negative, radius = ChartTheme.PointRadius.toPx() * 1.6f, center = Offset(toX(epoch), toY(value)))
            }
        }
        // Mark the latest value (the reader's anchor), as in the KPI sparkline.
        present.last().let { (epoch, value) ->
            drawCircle(stroke, radius = ChartTheme.PointRadius.toPx(), center = Offset(toX(epoch), toY(value)))
        }
    }
}
