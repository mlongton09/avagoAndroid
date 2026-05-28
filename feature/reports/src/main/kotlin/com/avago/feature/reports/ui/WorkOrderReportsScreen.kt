package com.avago.feature.reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.avago.feature.reports.ui.charts.BarChart
import com.avago.feature.reports.ui.components.RangePresetChips
import com.avago.feature.reports.ui.components.ReportTable
import com.avago.feature.reports.viewmodel.WorkOrderReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderReportsScreen(
    onBack: () -> Unit,
    viewModel: WorkOrderReportsViewModel = hiltViewModel(),
) {
    val rangePreset by viewModel.rangePreset.collectAsStateWithLifecycle()
    val openDashboard by viewModel.openDashboard.collectAsStateWithLifecycle()
    val pmCompliance by viewModel.pmCompliance.collectAsStateWithLifecycle()
    val mttr by viewModel.mttr.collectAsStateWithLifecycle()
    val completionRate by viewModel.completionRate.collectAsStateWithLifecycle()
    val techPerf by viewModel.techPerformance.collectAsStateWithLifecycle()
    val effortAcc by viewModel.effortAccuracy.collectAsStateWithLifecycle()
    val backlogAge by viewModel.backlogAge.collectAsStateWithLifecycle()
    val recurring by viewModel.recurringIssues.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Work Orders Reports") },
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

            // 1. Open Dashboard
            item {
                ReportCard(title = "Open Dashboard") {
                    val data = openDashboard
                    if (data == null) {
                        LoadingRow()
                    } else {
                        ReportTable(
                            headers = listOf("Status", "Count"),
                            rows = data.byStatus.entries.map { listOf(it.key, it.value.toString()) } +
                                listOf(
                                    listOf("Total Open", data.totalOpen.toString()),
                                    listOf("Overdue", data.overdue.toString()),
                                    listOf("Avg Age (days)", "%.1f".format(data.avgAgeDays)),
                                ),
                        )
                        if (data.byStatus.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            BarChart(
                                data = data.byStatus.mapValues { it.value.toDouble() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }

            // 2. PM Compliance
            item {
                ReportCard(title = "PM Compliance") {
                    val data = pmCompliance
                    if (data == null) {
                        LoadingRow()
                    } else {
                        SummaryRow("Scheduled", data.scheduled.toString())
                        SummaryRow("Completed", data.completed.toString())
                        SummaryRow("Compliance", "%.1f%%".format(data.compliancePct))
                        Spacer(Modifier.height(8.dp))
                        ReportTable(
                            headers = listOf("Asset Type", "Scheduled", "Completed", "Compliance %"),
                            rows = data.byAssetType.map {
                                listOf(
                                    it.assetType,
                                    it.scheduled.toString(),
                                    it.completed.toString(),
                                    "%.1f%%".format(it.compliancePct),
                                )
                            },
                        )
                    }
                }
            }

            // 3. MTTR
            item {
                ReportCard(title = "Mean Time to Resolution (MTTR)") {
                    val data = mttr
                    if (data == null) {
                        LoadingRow()
                    } else {
                        SummaryRow("Overall Avg (hrs)", "%.2f".format(data.avgHours))
                        Spacer(Modifier.height(8.dp))
                        if (data.byPriority.isNotEmpty()) {
                            Text(
                                "By Priority",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            ReportTable(
                                headers = listOf("Priority", "Avg Hours"),
                                rows = data.byPriority.entries.map {
                                    listOf(it.key, "%.2f".format(it.value))
                                },
                            )
                        }
                        if (data.byAssetType.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "By Asset Type",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            ReportTable(
                                headers = listOf("Asset Type", "Avg Hours"),
                                rows = data.byAssetType.entries.map {
                                    listOf(it.key, "%.2f".format(it.value))
                                },
                            )
                        }
                    }
                }
            }

            // 4. Completion Rate
            item {
                ReportCard(title = "Completion Rate") {
                    val data = completionRate
                    if (data == null) {
                        LoadingRow()
                    } else {
                        SummaryRow("Overall Rate", "%.1f%%".format(data.overallRate))
                        Spacer(Modifier.height(8.dp))
                        ReportTable(
                            headers = listOf("Period", "Created", "Completed", "Rate %"),
                            rows = data.points.map { p ->
                                val rate = if (p.created == 0) 0.0 else p.completed.toDouble() / p.created * 100
                                listOf(p.periodLabel, p.created.toString(), p.completed.toString(), "%.1f%%".format(rate))
                            },
                        )
                    }
                }
            }

            // 5. Tech Performance
            item {
                ReportCard(title = "Tech Performance") {
                    val rows = techPerf
                    if (rows == null) {
                        LoadingRow()
                    } else {
                        ReportTable(
                            headers = listOf("Technician", "WOs Completed", "Avg MTTR (hrs)", "Total Cost"),
                            rows = rows.map {
                                listOf(
                                    it.techName,
                                    it.wosCompleted.toString(),
                                    "%.2f".format(it.avgMttrHours),
                                    "$%.2f".format(it.totalCost),
                                )
                            },
                        )
                    }
                }
            }

            // 6. Effort Accuracy
            item {
                ReportCard(title = "Effort Accuracy") {
                    val rows = effortAcc
                    if (rows == null) {
                        LoadingRow()
                    } else {
                        ReportTable(
                            headers = listOf("Category", "Est. Hours", "Actual Hours", "Variance %"),
                            rows = rows.map {
                                listOf(
                                    it.category,
                                    "%.2f".format(it.estimatedHours),
                                    "%.2f".format(it.actualHours),
                                    "%.1f%%".format(it.variancePct),
                                )
                            },
                        )
                    }
                }
            }

            // 7. Backlog Age
            item {
                ReportCard(title = "Backlog Age") {
                    val data = backlogAge
                    if (data == null) {
                        LoadingRow()
                    } else {
                        SummaryRow("Total Open", data.totalOpen.toString())
                        Spacer(Modifier.height(8.dp))
                        BarChart(
                            data = data.buckets.associate { it.label to it.count.toDouble() },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        ReportTable(
                            headers = listOf("Age Bucket", "Count"),
                            rows = data.buckets.map { listOf(it.label, it.count.toString()) },
                        )
                    }
                }
            }

            // 8. Recurring Issues
            item {
                ReportCard(title = "Recurring Issues") {
                    val rows = recurring
                    if (rows == null) {
                        LoadingRow()
                    } else if (rows.isEmpty()) {
                        Text(
                            "No recurring issues in this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ReportTable(
                            headers = listOf("Asset", "Category", "Repeat Count"),
                            rows = rows.map {
                                listOf(
                                    it.assetName ?: it.assetId.take(8),
                                    it.category,
                                    it.repeatCount.toString(),
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
private fun ReportCard(title: String, content: @Composable () -> Unit) {
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
private fun SummaryRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
