package com.avago.feature.reports.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.reports.model.CostGroupMode
import com.avago.core.reports.model.CostGroupRow
import com.avago.core.reports.model.CostPeriodMode
import com.avago.core.reports.model.CostReportData
import com.avago.feature.reports.viewmodel.CostReportViewModel

private val CATEGORY_COLORS = listOf(
    Color(0xFF2196F3), // Blue
    Color(0xFF4CAF50), // Green
    Color(0xFFFF9800), // Orange
    Color(0xFFF44336), // Red
    Color(0xFF9C27B0), // Purple
    Color(0xFF009688), // Teal
    Color(0xFFE91E63), // Pink
    Color(0xFF3F51B5), // Indigo
)

private val COL_WIDTH: Dp = 68.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostReportScreen(
    onBack: () -> Unit,
    viewModel: CostReportViewModel = hiltViewModel(),
) {
    val groupMode by viewModel.groupMode.collectAsStateWithLifecycle()
    val periodMode by viewModel.periodMode.collectAsStateWithLifecycle()
    val periodOffset by viewModel.periodOffset.collectAsStateWithLifecycle()
    val expandedKeys by viewModel.expandedKeys.collectAsStateWithLifecycle()
    val expandedInventory by viewModel.expandedInventory.collectAsStateWithLifecycle()
    val data by viewModel.reportData.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cost Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Group mode pills ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CostGroupMode.entries.forEach { mode ->
                        FilterChip(
                            selected = groupMode == mode,
                            onClick = { viewModel.setGroupMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        CostGroupMode.ALL -> "All"
                                        CostGroupMode.BY_ASSET -> "By Asset"
                                        CostGroupMode.BY_TYPE -> "By Type"
                                        CostGroupMode.TCO -> "TCO"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }

            // ── Bar chart ─────────────────────────────────────────────────────
            item {
                CostBarChart(
                    data = data,
                    groupMode = groupMode,
                    currencyCode = currencyCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 8.dp),
                )
            }

            // ── Period navigation + YR/MO toggle (hidden in TCO) ─────────────
            if (groupMode != CostGroupMode.TCO) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.prevPeriod() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                        }
                        Text(
                            text = viewModel.periodRangeLabel(data),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(
                            onClick = { viewModel.nextPeriod() },
                            enabled = viewModel.canNavigateNext(),
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                        // YR / MO toggle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp),
                        ) {
                            listOf(CostPeriodMode.YEAR to "YR", CostPeriodMode.MONTH to "MO").forEach { (mode, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (periodMode == mode) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable { viewModel.setPeriodMode(mode) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (periodMode == mode) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }

            // ── Expand/Collapse all (By Asset mode only) ─────────────────────
            if (groupMode == CostGroupMode.BY_ASSET && data != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TextButton(onClick = { viewModel.expandAll(data!!.rows.map { it.key }) }) {
                            Text("+ Expand All", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { viewModel.collapseAll() }) {
                            Text("− Collapse All", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Loading ───────────────────────────────────────────────────────
            if (data == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                return@LazyColumn
            }

            val reportData = data!!

            // ── Column header row ─────────────────────────────────────────────
            item {
                HorizontalDivider()
                CostHeaderRow(
                    periods = if (groupMode == CostGroupMode.TCO)
                        listOf("", "", "TCO")
                    else
                        reportData.periods.map { it.label },
                )
                HorizontalDivider()
            }

            // ── Data rows ─────────────────────────────────────────────────────
            items(reportData.rows, key = { it.key }) { row ->
                val isExpanded = row.key in expandedKeys
                CostGroupRowItem(
                    row = row,
                    isExpanded = isExpanded,
                    isTco = groupMode == CostGroupMode.TCO,
                    currencyCode = currencyCode,
                    onToggle = { viewModel.toggleExpanded(row.key) },
                )
                AnimatedVisibility(visible = isExpanded) {
                    Column {
                        row.categories.forEach { cat ->
                            CostCategoryRowItem(
                                label = cat.category,
                                costs = cat.costs,
                                colorIndex = cat.colorIndex,
                                isTco = groupMode == CostGroupMode.TCO,
                                indent = true,
                                currencyCode = currencyCode,
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }

            // ── Inventory Value (All mode only) ───────────────────────────────
            if (groupMode == CostGroupMode.ALL && reportData.inventoryValue > 0) {
                item {
                    CostInventoryGroupRow(
                        value = reportData.inventoryValue,
                        isExpanded = expandedInventory,
                        currencyCode = currencyCode,
                        onToggle = { viewModel.toggleInventory() },
                    )
                    AnimatedVisibility(visible = expandedInventory) {
                        Column {
                            reportData.inventoryByCategory.forEachIndexed { idx, (cat, value) ->
                                CostInventoryCategoryRow(
                                    label = cat,
                                    value = value,
                                    colorIndex = idx % 8,
                                    indent = true,
                                    currencyCode = currencyCode,
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── Bar chart (period totals or group totals) ────────────────────────────────

@Composable
private fun CostBarChart(
    data: CostReportData?,
    groupMode: CostGroupMode,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val emptyText = "No cost data yet"

    val bars: List<Pair<String, Double>> = when {
        data == null -> emptyList()
        groupMode == CostGroupMode.TCO ->
            data.rows.take(8).map { it.label to it.lifetimeCost }
        else ->
            data.periods.mapIndexed { i, p ->
                p.label to (data.rows.firstOrNull()?.periodCosts?.getOrElse(i) { 0.0 }
                    ?.let { if (data.rows.size == 1) it else data.rows.sumOf { r -> r.periodCosts.getOrElse(i) { 0.0 } } }
                    ?: 0.0)
            }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val baselineY = size.height - 36f
        val chartH = baselineY - 20f

        // Baseline
        drawLine(
            color = dividerColor,
            start = Offset(8f, baselineY),
            end = Offset(size.width - 8f, baselineY),
            strokeWidth = 1f,
        )

        if (bars.isEmpty() || bars.all { it.second <= 0.0 }) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    emptyText,
                    size.width / 2,
                    size.height / 2,
                    android.graphics.Paint().apply {
                        color = surfaceColor.copy(alpha = 0.5f).toArgb()
                        textSize = 36f
                        textAlign = android.graphics.Paint.Align.CENTER
                    },
                )
            }
            return@Canvas
        }

        val maxVal = bars.maxOf { it.second }.coerceAtLeast(1.0)
        val n = bars.size
        val availW = size.width - 16f
        val barW = (availW / n * 0.6f).coerceAtMost(52f)
        val gap = (availW - barW * n) / (n + 1)

        bars.forEachIndexed { i, (label, value) ->
            val frac = (value / maxVal).toFloat()
            val barH = (frac * chartH).coerceAtLeast(if (value > 0) 2f else 0f)
            val x = 8f + gap + (barW + gap) * i
            val barColor = CATEGORY_COLORS[i % CATEGORY_COLORS.size]

            drawRoundRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(x, baselineY - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(4f),
            )

            drawIntoCanvas { canvas ->
                // Value label above bar
                if (value > 0) {
                    canvas.nativeCanvas.drawText(
                        shortAmount(value, currencyCode),
                        x + barW / 2,
                        (baselineY - barH - 4f).coerceAtLeast(16f),
                        android.graphics.Paint().apply {
                            color = barColor.toArgb()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        },
                    )
                }
                // Bar label below baseline
                canvas.nativeCanvas.drawText(
                    label.take(8),
                    x + barW / 2,
                    size.height - 4f,
                    android.graphics.Paint().apply {
                        color = surfaceColor.copy(alpha = 0.7f).toArgb()
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                    },
                )
            }
        }
    }
}

private fun shortAmount(v: Double, currencyCode: String): String {
    val symbol = runCatching {
        java.util.Currency.getInstance(currencyCode.uppercase()).symbol
    }.getOrDefault("$")
    return when {
        v >= 1_000_000 -> "$symbol%.1fM".format(v / 1_000_000)
        v >= 1_000 -> "$symbol%.0fk".format(v / 1_000)
        else -> "$symbol%.0f".format(v)
    }
}

// ─── Header row ──────────────────────────────────────────────────────────────

@Composable
private fun CostHeaderRow(periods: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 6.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        periods.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.width(COL_WIDTH),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Group row (expandable) ───────────────────────────────────────────────────

@Composable
private fun CostGroupRowItem(
    row: CostGroupRow,
    isExpanded: Boolean,
    isTco: Boolean,
    currencyCode: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = row.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isTco) {
            // First two columns blank, third shows lifetime
            Spacer(Modifier.width(COL_WIDTH))
            Spacer(Modifier.width(COL_WIDTH))
            Text(
                text = if (row.lifetimeCost > 0) shortAmount(row.lifetimeCost, currencyCode) else "–",
                modifier = Modifier.width(COL_WIDTH),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (row.lifetimeCost > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            row.periodCosts.forEach { v ->
                Text(
                    text = if (v > 0) shortAmount(v, currencyCode) else "–",
                    modifier = Modifier.width(COL_WIDTH),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (v > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Category row (indented, colored dot) ────────────────────────────────────

@Composable
private fun CostCategoryRowItem(
    label: String,
    costs: List<Double>,
    colorIndex: Int,
    isTco: Boolean,
    indent: Boolean,
    currencyCode: String,
) {
    val dotColor = CATEGORY_COLORS[colorIndex % CATEGORY_COLORS.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(
                start = if (indent) 32.dp else 12.dp,
                end = 12.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isTco) {
            Spacer(Modifier.width(COL_WIDTH))
            Spacer(Modifier.width(COL_WIDTH))
            val v = costs.getOrElse(2) { 0.0 }
            Text(
                text = if (v > 0) shortAmount(v, currencyCode) else "–",
                modifier = Modifier.width(COL_WIDTH),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                color = if (v > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            costs.forEach { v ->
                Text(
                    text = if (v > 0) shortAmount(v, currencyCode) else "–",
                    modifier = Modifier.width(COL_WIDTH),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (v > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Inventory group row ──────────────────────────────────────────────────────

@Composable
private fun CostInventoryGroupRow(
    value: Double,
    isExpanded: Boolean,
    currencyCode: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Inventory Value",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
        // First two columns: dash (point-in-time, no period)
        Text(
            text = "–",
            modifier = Modifier.width(COL_WIDTH),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "–",
            modifier = Modifier.width(COL_WIDTH),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (value > 0) shortAmount(value, currencyCode) else "–",
            modifier = Modifier.width(COL_WIDTH),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = if (value > 0) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Inventory category row ───────────────────────────────────────────────────

@Composable
private fun CostInventoryCategoryRow(
    label: String,
    value: Double,
    colorIndex: Int,
    indent: Boolean,
    currencyCode: String,
) {
    val dotColor = CATEGORY_COLORS[colorIndex % CATEGORY_COLORS.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(
                start = if (indent) 32.dp else 12.dp,
                end = 12.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = "–", modifier = Modifier.width(COL_WIDTH), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "–", modifier = Modifier.width(COL_WIDTH), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (value > 0) shortAmount(value, currencyCode) else "–",
            modifier = Modifier.width(COL_WIDTH),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = if (value > 0) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
