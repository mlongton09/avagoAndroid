package com.avago.feature.reports.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.avago.feature.reports.ui.charts.LineChart
import com.avago.feature.reports.ui.components.RangePresetChips
import com.avago.feature.reports.ui.components.ReportTable
import com.avago.feature.reports.viewmodel.FinancialReportsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(
    onBack: () -> Unit,
    viewModel: FinancialReportsViewModel = hiltViewModel(),
) {
    val rangePreset by viewModel.rangePreset.collectAsStateWithLifecycle()
    val itemizedCost by viewModel.itemizedCost.collectAsStateWithLifecycle()
    val transactionJournal by viewModel.transactionJournal.collectAsStateWithLifecycle()
    val periodClose by viewModel.periodClose.collectAsStateWithLifecycle()
    val vendorSummary by viewModel.vendorSummary1099.collectAsStateWithLifecycle()
    val fixedAssets by viewModel.fixedAssetRegister.collectAsStateWithLifecycle()
    val costByVendor by viewModel.costByVendor.collectAsStateWithLifecycle()
    val costByPerformedBy by viewModel.costByPerformedBy.collectAsStateWithLifecycle()
    val partsSpend by viewModel.partsSpendTrend.collectAsStateWithLifecycle()
    val inventoryInvestment by viewModel.inventoryInvestment.collectAsStateWithLifecycle()
    val repairVsReplace by viewModel.repairVsReplace.collectAsStateWithLifecycle()

    val tz = TimeZone.currentSystemDefault()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Financial Reports") },
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

            // 14. Itemized Cost (Cost Lines)
            item {
                FinCard(title = "Itemized Cost") {
                    val lines = itemizedCost
                    if (lines == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Description", "Kind", "Qty", "Unit Cost", "Tax", "GL Code"),
                            rows = lines.take(200).map { line ->
                                listOf(
                                    line.description ?: "–",
                                    line.kind,
                                    "%.2f".format(line.quantity),
                                    "$%.4f".format(line.unitCost),
                                    line.taxAmount?.let { "$%.4f".format(it) } ?: "–",
                                    line.glCode ?: "–",
                                )
                            },
                        )
                        if ((lines.size) > 200) {
                            Text(
                                "Showing 200 of ${lines.size}. Export for full data.",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 15. Transaction Journal
            item {
                FinCard(title = "Transaction Journal") {
                    val logs = transactionJournal
                    if (logs == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Date", "Title", "Category", "Performed By", "Cost"),
                            rows = logs.take(200).map { log ->
                                val dt = Instant.fromEpochMilliseconds(log.entryDate).toLocalDateTime(tz)
                                listOf(
                                    "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}",
                                    log.title,
                                    log.category ?: "–",
                                    log.performedBy ?: "–",
                                    log.cost?.let { "$%.2f".format(it) } ?: "–",
                                )
                            },
                        )
                    }
                }
            }

            // 16. Period Close
            item {
                FinCard(title = "Period Close Pack") {
                    val rows = periodClose
                    if (rows == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Period", "Log Count", "Total", "Labor", "Parts", "Tax"),
                            rows = rows.map { r ->
                                listOf(
                                    r.period,
                                    r.logCount.toString(),
                                    "$%.2f".format(r.totalCost),
                                    "$%.2f".format(r.laborCost),
                                    "$%.2f".format(r.partsCost),
                                    "$%.2f".format(r.taxCost),
                                )
                            },
                        )
                    }
                }
            }

            // 17. 1099 Vendor Summary
            item {
                FinCard(title = "1099 Vendor Summary") {
                    val rows = vendorSummary
                    if (rows == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Vendor / Performed By", "Total Cost", "Log Count", "Flag 1099"),
                            rows = rows.map { r ->
                                listOf(
                                    r.vendorName,
                                    "$%.2f".format(r.totalCost),
                                    r.logCount.toString(),
                                    if (r.flag1099) "Yes ⚠" else "No",
                                )
                            },
                        )
                    }
                }
            }

            // 18. Fixed Asset Register
            item {
                FinCard(title = "Fixed Asset Register") {
                    val rows = fixedAssets
                    if (rows == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Asset", "Type", "Purchase Price", "Age (yrs)", "SL Dep.", "DDB Dep.", "NBV"),
                            rows = rows.map { r ->
                                listOf(
                                    r.name,
                                    r.assetType ?: "–",
                                    "$%.2f".format(r.purchasePrice),
                                    "%.1f".format(r.ageYears),
                                    "$%.2f".format(r.straightLineDepreciation),
                                    "$%.2f".format(r.doubleDecliningDepreciation),
                                    "$%.2f".format(r.netBookValue),
                                )
                            },
                        )
                    }
                }
            }

            // 19. Cost by Vendor
            item {
                FinCard(title = "Cost by Vendor") {
                    val rows = costByVendor
                    if (rows == null) {
                        FinLoading()
                    } else {
                        if (rows.isNotEmpty()) {
                            BarChart(
                                data = rows.take(10).associate { it.vendorName to it.periodCost },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        ReportTable(
                            headers = listOf("Vendor", "Period Cost", "YTD Cost", "Log Count"),
                            rows = rows.map { r ->
                                listOf(
                                    r.vendorName,
                                    "$%.2f".format(r.periodCost),
                                    "$%.2f".format(r.ytdCost),
                                    r.logCount.toString(),
                                )
                            },
                        )
                    }
                }
            }

            // 20. Cost by Performed-By
            item {
                FinCard(title = "Cost by Performed-By") {
                    val rows = costByPerformedBy
                    if (rows == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Performed By", "Total Cost", "Log Count"),
                            rows = rows.map { r ->
                                listOf(r.performedBy, "$%.2f".format(r.totalCost), r.logCount.toString())
                            },
                        )
                    }
                }
            }

            // 21. Parts Spend Trend
            item {
                FinCard(title = "Parts Spend Trend") {
                    val points = partsSpend
                    if (points == null) {
                        FinLoading()
                    } else {
                        if (points.size >= 2) {
                            LineChart(
                                points = points.mapIndexed { i, p -> Pair(i.toLong(), p.totalSpend) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        ReportTable(
                            headers = listOf("Period", "Total Spend", "Transactions"),
                            rows = points.map { p ->
                                listOf(p.period, "$%.2f".format(p.totalSpend), p.transactionCount.toString())
                            },
                        )
                    }
                }
            }

            // 22. Inventory Investment
            item {
                FinCard(title = "Inventory Investment") {
                    val rows = inventoryInvestment
                    if (rows == null) {
                        FinLoading()
                    } else {
                        if (rows.isNotEmpty()) {
                            BarChart(
                                data = rows.associate { it.category to it.totalValue },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        ReportTable(
                            headers = listOf("Category", "Part Count", "Total Value"),
                            rows = rows.map { r ->
                                listOf(r.category, r.partCount.toString(), "$%.2f".format(r.totalValue))
                            },
                        )
                    }
                }
            }

            // 23. Repair vs Replace
            item {
                FinCard(title = "Repair vs. Replace") {
                    val rows = repairVsReplace
                    if (rows == null) {
                        FinLoading()
                    } else {
                        ReportTable(
                            headers = listOf("Asset", "Type", "Purchase Price", "Lifetime Cost", "NBV", "Recommendation"),
                            rows = rows.map { r ->
                                listOf(
                                    r.name,
                                    r.assetType ?: "–",
                                    "$%.2f".format(r.purchasePrice),
                                    "$%.2f".format(r.lifetimeCost),
                                    "$%.2f".format(r.netBookValue),
                                    r.recommendation,
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
private fun FinCard(title: String, content: @Composable () -> Unit) {
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
private fun FinLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
