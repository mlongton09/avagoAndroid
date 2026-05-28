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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkOutline
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

private data class ReportSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val reportCount: Int,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsListScreen(
    onNavigateToWorkOrders: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToFinancial: () -> Unit,
    onNavigateToSystem: () -> Unit,
) {
    val sections = listOf(
        ReportSection(
            title = "Work Orders",
            subtitle = "8 reports — open dashboard, PM compliance, MTTR, backlog age & more",
            icon = Icons.Default.WorkOutline,
            reportCount = 8,
            onClick = onNavigateToWorkOrders,
        ),
        ReportSection(
            title = "Maintenance",
            subtitle = "5 reports — service history, frequency, meter trend, inspection rate",
            icon = Icons.Default.Build,
            reportCount = 5,
            onClick = onNavigateToMaintenance,
        ),
        ReportSection(
            title = "Financial",
            subtitle = "10 reports — cost lines, journal, 1099, fixed assets, repair-vs-replace",
            icon = Icons.Default.AccountBalance,
            reportCount = 10,
            onClick = onNavigateToFinancial,
        ),
        ReportSection(
            title = "System",
            subtitle = "Coming soon — account overview, inventory health, compliance",
            icon = Icons.Default.Settings,
            reportCount = 5,
            onClick = onNavigateToSystem,
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
                Text(
                    "${section.reportCount} reports",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}
