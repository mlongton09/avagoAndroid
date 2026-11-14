package com.avago.feature.reports.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avago.core.design.chart.ChartColors
import com.avago.core.design.chart.ChartFormat
import com.avago.core.design.chart.ChartLayout
import com.avago.core.design.chart.ChartTheme
import com.avago.core.design.chart.chartTextPaint
import com.avago.core.design.chart.drawChartEmpty
import com.avago.core.design.chart.drawChartSkeleton
import com.avago.core.design.chart.drawChartZeroLine
import com.avago.feature.reports.R
import kotlin.math.abs

/**
 * Column chart on Canvas, styled by [ChartTheme] (AvagoWeb
 * docs/chart-style-guide.md): bars always start at an emphasised zero
 * baseline (negatives such as credits render below it), no gridlines because
 * every bar carries a direct value label, category labels auto-skip instead of
 * rotating, and data labels are compact and hidden when they would collide.
 *
 * [data] is ordered (label → value). [formatCompact] formats data labels;
 * [contentDescription] is the accessible summary — the plot is exposed to
 * TalkBack as one image, so it must state the key figures in full.
 * [barColor] picks a colour per bar index (default: one series, `categorical[0]`).
 */
@Composable
fun BarChart(
    data: List<Pair<String, Double>>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    state: ChartState = ChartState.READY,
    height: Dp = 200.dp,
    formatCompact: (Double) -> String = { ChartFormat.numberCompact(it) },
    barColor: (index: Int, colors: ChartColors) -> Color = { _, c -> c.categorical(0) },
) {
    val colors = ChartTheme.colors()
    val emptyText = stringResource(R.string.reports_empty_state)
    val loadingText = stringResource(R.string.chart_loading)
    val effectiveState = if (state == ChartState.READY && data.isEmpty()) ChartState.EMPTY else state
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

        val tickPaint = chartTextPaint(colors.textMuted)
        val valuePaint = chartTextPaint(colors.textMuted, ChartTheme.DataLabelTextSize, bold = true)
        val gap = 4.dp.toPx()
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = valuePaint.textSize + gap * 2
        val bottom = size.height - tickPaint.textSize - gap * 2

        // Scale always includes zero; negatives render below the baseline.
        val maxVal = maxOf(data.maxOf { it.second }, 0.0)
        val minVal = minOf(data.minOf { it.second }, 0.0)
        val hasNegative = minVal < 0.0
        // Leave room under the baseline for labels of negative bars.
        val plotBottom = if (hasNegative) bottom - valuePaint.textSize - gap else bottom
        val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        fun yOf(v: Double): Float = (plotBottom - (v - minVal) / span * (plotBottom - top)).toFloat()
        val zeroY = yOf(0.0)

        val n = data.size
        val slot = (right - left) / n
        val barW = (slot * 0.6f).coerceAtMost(ChartTheme.BarMaxWidth.toPx())
        val radius = CornerRadius(ChartTheme.BarCornerRadius.toPx())
        val native = drawContext.canvas.nativeCanvas

        // Category labels: auto-skip (never rotate) when the widest would overlap.
        val widest = data.maxOf { tickPaint.measureText(it.first) } + gap * 2
        val step = ChartLayout.labelStep(widest, slot)

        // Data labels: all or none, so a reader never compares labelled with unlabelled bars.
        val valueTexts = data.map { formatCompact(it.second) }
        val showValues = valueTexts.all { ChartLayout.dataLabelFits(valuePaint.measureText(it), slot, n) }

        data.forEachIndexed { i, (label, value) ->
            val cx = left + slot * i + slot / 2f
            val y = yOf(value)
            val barTop = minOf(y, zeroY)
            val barH = abs(zeroY - y)
            if (barH > 0f) {
                drawRoundRect(
                    color = barColor(i, colors),
                    topLeft = Offset(cx - barW / 2f, barTop),
                    size = Size(barW, barH),
                    cornerRadius = radius,
                )
            }
            if (showValues) {
                val labelY = if (value >= 0) barTop - gap else barTop + barH + valuePaint.textSize + gap
                native.drawText(valueTexts[i], cx, labelY, valuePaint)
            }
            if (i % step == 0) {
                native.drawText(ellipsize(label, slot * step - gap, tickPaint), cx, size.height - gap, tickPaint)
            }
        }

        // Emphasised zero baseline drawn last so it sits over bar bottoms.
        drawChartZeroLine(zeroY, left, right, colors)
    }
}

/** Trim [text] with an ellipsis to fit [maxWidth] (labels are never rotated). */
internal fun ellipsize(text: String, maxWidth: Float, paint: android.graphics.Paint): String {
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "\u2026"
    var end = text.length
    while (end > 1 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--
    return text.substring(0, end) + ellipsis
}
