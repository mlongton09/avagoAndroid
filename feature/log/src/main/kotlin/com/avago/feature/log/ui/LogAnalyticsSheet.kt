package com.avago.feature.log.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.design.chart.ChartFormat
import com.avago.core.design.chart.ChartTheme
import com.avago.core.design.chart.drawChartGridLine
import com.avago.core.ui.categoryBadgeColor
import com.avago.core.ui.categoryIconName
import com.avago.feature.log.R
import com.avago.feature.log.model.LogAnalytics
import java.text.NumberFormat

/**
 * Asset analytics bottom sheet — mobile adaptation of web `AssetAnalyticsPanel`:
 * KPI cards (total spend, cost per meter unit, average economy), a fuel-economy sparkline
 * with anomaly dots, and spend-by-category bars. Tapping a category applies it as the list
 * filter (one-handed drill-down instead of the web's separate select).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogAnalyticsSheet(
    summary: LogAnalytics.Summary,
    currencyCode: String,
    onCategoryClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    // Shared chart formatter: currency minor units, whole units from 1,000 and
    // accounting negatives (credits) — AvagoWeb docs/chart-style-guide.md § 5.
    fun fmtMoney(amount: Double) = ChartFormat.money(amount, currencyCode, locale)
    val chartColors = ChartTheme.colors()
    val num1 = remember { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
    val num0 = remember { NumberFormat.getIntegerInstance(locale) }
    val unitLabel = distanceUnitLabel(summary.costPerMeter.unit)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.log_analytics_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )

            // ── KPIs ───────────────────────────────────────────────────────
            KpiCard(
                label = stringResource(R.string.log_analytics_kpi_total_spend),
                value = fmtMoney(summary.totalSpend),
                sub = pluralStringResource(R.plurals.log_analytics_kpi_entries, summary.entries, summary.entries),
            )
            val cpm = summary.costPerMeter
            KpiCard(
                label = stringResource(R.string.log_analytics_kpi_cost_per, unitLabel),
                value = cpm.perUnit?.let { fmtMoney(it) } ?: "—",
                sub = if (cpm.perUnit != null) {
                    stringResource(R.string.log_analytics_kpi_over, num0.format(cpm.distance), unitLabel)
                } else {
                    stringResource(R.string.log_analytics_kpi_need_meter)
                },
            )
            val econ = summary.economy
            KpiCard(
                label = stringResource(R.string.log_analytics_kpi_avg_economy),
                value = econ.average?.let { "${num1.format(it)} ${econ.unit}" } ?: "—",
                sub = econ.last?.let { stringResource(R.string.log_analytics_kpi_last, "${num1.format(it)} ${econ.unit}") }
                    ?: stringResource(R.string.log_analytics_kpi_need_full_fills),
            )

            // ── Economy trend ──────────────────────────────────────────────
            SectionTitle(stringResource(R.string.log_analytics_econ_trend, econ.unit))
            val pts = econ.withEconomy
            if (pts.size >= 2) {
                val description = stringResource(
                    R.string.log_analytics_econ_trend_cd,
                    pts.size,
                    num1.format(pts.minOf { it.economy!! }),
                    num1.format(pts.maxOf { it.economy!! }),
                    econ.unit,
                    econ.anomalies,
                )
                EconomySparkline(
                    points = pts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .semantics { contentDescription = description },
                )
                if (econ.anomalies > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(chartColors.negative),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pluralStringResource(R.plurals.log_analytics_econ_anomalies, econ.anomalies, econ.anomalies),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.log_analytics_econ_need_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Spend by category ─────────────────────────────────────────
            SectionTitle(stringResource(R.string.log_analytics_spend_by_category))
            if (summary.spend.isEmpty()) {
                Text(
                    text = stringResource(R.string.log_analytics_spend_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val max = summary.spend.maxOf { it.total }.takeIf { it > 0 } ?: 1.0
                // Bars start at zero; a net credit (negative total) draws no bar, its amount shows in parentheses.
                val otherLabel = stringResource(R.string.log_analytics_category_other)
                summary.spend.forEach { row ->
                    val label = if (row.category == "other") otherLabel else categoryDisplayLabel(row.category)
                    SpendRow(
                        label = label,
                        amount = fmtMoney(row.total),
                        fraction = (row.total.coerceAtLeast(0.0) / max).toFloat(),
                        color = categoryBadgeColor(categoryIconName(row.category)),
                        onClick = { onCategoryClick(row.category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun distanceUnitLabel(unit: LogAnalytics.DistanceUnit): String = when (unit) {
    LogAnalytics.DistanceUnit.MI -> stringResource(R.string.log_analytics_unit_mi)
    LogAnalytics.DistanceUnit.KM -> stringResource(R.string.log_analytics_unit_km)
    LogAnalytics.DistanceUnit.H -> stringResource(R.string.log_analytics_unit_h)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun KpiCard(label: String, value: String, sub: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EconomySparkline(points: List<LogAnalytics.EconomyPoint>, modifier: Modifier = Modifier) {
    // Sparkline per the chart style guide: light gridlines, 2dp line without
    // fill, points hidden except anomalies (larger, `negative`) and the latest
    // value. Fuel economy has no meaningful zero, so the axis is fitted to the data.
    val colors = ChartTheme.colors()
    val line = colors.categorical(0)
    val anomaly = colors.negative
    Canvas(modifier = modifier) {
        val values = points.map { it.economy!! }
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun x(i: Int) = pad + if (points.size == 1) w / 2 else w * i / (points.size - 1)
        fun y(v: Double) = pad + h - (h * ((v - min) / span)).toFloat()
        for (q in 0..2) drawChartGridLine(pad + h * q / 2f, pad, size.width - pad, colors)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(
            path,
            line,
            style = Stroke(width = ChartTheme.LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEachIndexed { i, p ->
            if (p.anomaly) drawCircle(anomaly, radius = 5.dp.toPx(), center = Offset(x(i), y(p.economy!!)))
        }
        if (!points.last().anomaly) {
            drawCircle(line, radius = ChartTheme.PointRadius.toPx(), center = Offset(x(points.lastIndex), y(values.last())))
        }
    }
}

@Composable
private fun SpendRow(
    label: String,
    amount: String,
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
    }
}
