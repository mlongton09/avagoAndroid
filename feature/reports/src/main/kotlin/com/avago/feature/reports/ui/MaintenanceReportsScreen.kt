package com.avago.feature.reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.reports.ui.charts.DonutChart
import com.avago.feature.reports.ui.charts.LineChart
import com.avago.feature.reports.ui.components.RangePresetChips
import com.avago.feature.reports.ui.components.ReportTable
import com.avago.feature.reports.viewmodel.MaintenanceReportsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceReportsScreen(
    onBack: () -> Unit,
    viewModel: MaintenanceReportsViewModel = hiltViewModel(),
) {
    val rangePreset by viewModel.rangePreset.collectAsStateWithLifecycle()
    val serviceHistory by viewModel.serviceHistory.collectAsStateWithLifecycle()
    val serviceFrequency by viewModel.serviceFrequency.collectAsStateWithLifecycle()
    val meterTrend by viewModel.meterTrend.collectAsStateWithLifecycle()
    val inspectionRate by viewModel.inspectionRate.collectAsStateWithLifecycle()
    val serviceMix by viewModel.serviceMix.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Maintenance Reports") },
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
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                RangePresetChips(
                    selected = rangePreset,
                    onSelect = viewModel::setPreset,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                HorizontalDivider()
            }

            // 9. Service History
            item {
                MaintReportCard(title = "Service History") {
                    val logs = serviceHistory
                    if (logs == null) {
                        MaintLoading()
                    } else {
                        val tz = TimeZone.currentSystemDefault()
                        ReportTable(
                            headers = listOf("Date", "Asset", "Title", "Category", "Cost"),
                            rows = logs.take(100).map { log ->
                                val dt = Instant.fromEpochMilliseconds(log.entryDate).toLocalDateTime(tz)
                                listOf(
                                    "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}",
                                    log.assetId.take(8),
                                    log.title,
                                    log.category ?: "–",
                                    log.cost?.let { "$%.2f".format(it) } ?: "–",
                                )
                            },
                        )
                        if (logs.size > 100) {
                            Text(
                                "Showing 100 of ${logs.size} entries. Export CSV/PDF for full data.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }

            // 10. Service Frequency
            item {
                MaintReportCard(title = "Service Frequency") {
                    val rows = serviceFrequency
                    if (rows == null) {
                        MaintLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Asset", "Type", "Services", "Avg Days Between"),
                            rows = rows.map {
                                listOf(
                                    it.assetName ?: it.assetId.take(8),
                                    it.category ?: "–",
                                    it.serviceCount.toString(),
                                    "%.1f".format(it.avgDaysBetweenServices),
                                )
                            },
                        )
                    }
                }
            }

            // 11. Meter Trend
            item {
                MaintReportCard(title = "Meter Trend") {
                    val points = meterTrend
                    if (points == null) {
                        // No asset selected
                        Text(
                            "Select an asset to view meter trend.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (points.isEmpty()) {
                        Text(
                            "No meter readings for this asset in the selected period.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LineChart(
                            points = points.map { Pair(it.epochMs, it.value) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        ReportTable(
                            headers = listOf("Date", "Reading"),
                            rows = points.map { p ->
                                val dt = Instant.fromEpochMilliseconds(p.epochMs)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                listOf(
                                    "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}",
                                    "%.2f".format(p.value),
                                )
                            },
                        )
                    }
                }
            }

            // 12. Inspection Rate
            item {
                MaintReportCard(title = "Inspection Rate") {
                    val data = inspectionRate
                    if (data == null) {
                        MaintLoading()
                    } else {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Logs"); Text(data.totalLogs.toString())
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Inspection Logs"); Text(data.inspectionLogs.toString())
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Inspection Rate"); Text("%.1f%%".format(data.ratePct))
                            }
                        }
                    }
                }
            }

            // 13. Service Mix
            item {
                MaintReportCard(title = "Service Mix") {
                    val rows = serviceMix
                    if (rows == null) {
                        MaintLoading()
                    } else {
                        DonutChart(
                            data = rows.associate { it.category to it.totalCost },
                            modifier = Modifier.padding(16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        ReportTable(
                            headers = listOf("Category", "Count", "Total Cost", "Cost %"),
                            rows = rows.map {
                                listOf(
                                    it.category,
                                    it.count.toString(),
                                    "$%.2f".format(it.totalCost),
                                    "%.1f%%".format(it.costPct),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintReportCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun MaintLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
