package com.avago.feature.reports.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.design.chart.ChartFormat
import com.avago.core.design.chart.ChartLayout
import com.avago.core.design.chart.ChartTheme
import com.avago.feature.reports.R

/**
 * Doughnut for composition at one point (2–6 parts), styled by [ChartTheme]
 * (AvagoWeb docs/chart-style-guide.md § 6–7): at most six slices — the tail
 * is grouped into "Other" in `neutral` — the total in the centre, and a
 * legend carrying each part's full value and share so colour is never the
 * only cue. Each legend row is read by TalkBack as one item.
 *
 * [data] is label → value; [formatValue] formats full values (e.g.
 * `ChartFormat.money`), [centerCaption] names the total ("Total").
 */
@Composable
fun DonutChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    state: ChartState = ChartState.READY,
    centerCaption: String? = null,
    formatValue: (Double) -> String = { ChartFormat.number(it, fractionDigits = 1) },
) {
    val colors = ChartTheme.colors()
    val otherLabel = stringResource(R.string.chart_other)
    val emptyText = stringResource(R.string.reports_empty_state)
    val loadingText = stringResource(R.string.chart_loading)
    // Shares of a negative total are meaningless; only positive parts compose a doughnut.
    val parts = data.filter { it.second > 0.0 }
    val effectiveState = if (state == ChartState.READY && parts.isEmpty()) ChartState.EMPTY else state

    if (effectiveState != ChartState.READY) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .semantics { contentDescription = if (effectiveState == ChartState.LOADING) loadingText else emptyText },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (effectiveState == ChartState.LOADING) loadingText else emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        return
    }

    val slices = ChartLayout.groupTail(parts.map { it.first to it.second }, ChartLayout.MAX_DONUT_SLICES, otherLabel)
    val total = slices.sumOf { it.second }
    fun sliceColor(i: Int, label: String): Color =
        if (label == otherLabel && i == slices.lastIndex && parts.size > ChartLayout.MAX_DONUT_SLICES) colors.neutral
        else colors.categorical(i)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(180.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 28.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(diameter, diameter)
                // 1.5° gap between slices so adjacent parts stay distinct without borders.
                val gap = if (slices.size > 1) 1.5f else 0f
                var startAngle = -90f
                slices.forEachIndexed { i, (label, value) ->
                    val sweep = (value / total * 360f).toFloat()
                    drawArc(
                        color = sliceColor(i, label),
                        startAngle = startAngle,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatValue(total),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                centerCaption?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Legend: one row per slice — swatch, label, full value, share.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, (label, value) ->
                val share = ChartFormat.percent(value / total, fractionDigits = 1)
                val full = formatValue(value)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val swatch = sliceColor(i, label)
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawRoundRect(swatch, cornerRadius = CornerRadius(2.dp.toPx()))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = full,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.text,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = share,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.textMuted,
                        modifier = Modifier.widthIn(min = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}
