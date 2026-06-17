package com.avago.feature.reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.WorkOutline
import com.avago.feature.reports.R
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

private data class ReportSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsListScreen(
    onNavigateToCategory: () -> Unit,
    onNavigateToCost: () -> Unit,
    onNavigateToKpiDashboard: () -> Unit = {},
) {
    val sections = listOf(
        ReportSection(
            title = "KPI Dashboard",
            subtitle = "MTTR, MTBF, PM compliance, and open WOs by priority",
            icon = Icons.Default.Dashboard,
            onClick = onNavigateToKpiDashboard,
        ),
        ReportSection(
            title = stringResource(R.string.report_category),
            subtitle = "Work orders grouped by category",
            icon = Icons.Default.WorkOutline,
            onClick = onNavigateToCategory,
        ),
        ReportSection(
            title = stringResource(R.string.report_cost),
            subtitle = "Costs by asset, type, and total cost of ownership",
            icon = Icons.Default.AccountBalance,
            onClick = onNavigateToCost,
        ),
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Reports") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Select a section to view detailed reports.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            sections.forEach { section ->
                ReportSectionCard(section = section)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSectionCard(section: ReportSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = section.onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        ListItem(
            headlineContent = { Text(section.title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(section.subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {

            },
        )
    }
}
