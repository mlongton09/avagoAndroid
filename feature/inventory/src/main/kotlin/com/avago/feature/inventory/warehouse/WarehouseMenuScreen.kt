package com.avago.feature.inventory.warehouse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseMenuScreen(
    onReceive: () -> Unit,
    onIssue: () -> Unit,
    onMove: () -> Unit,
    onReorder: () -> Unit,
    onGrnList: () -> Unit,
    onStockingLevels: () -> Unit,
    onBins: () -> Unit,
    onPartIssues: () -> Unit,
    onTransferRequests: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.warehouse_menu_title)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_receive_card),
                    onClick = onReceive,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_issue_card),
                    onClick = onIssue,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_move_card),
                    onClick = onMove,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_reorder_card),
                    onClick = onReorder,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_grn_history_card),
                    onClick = onGrnList,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_stocking_levels_card),
                    onClick = onStockingLevels,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_bins_card),
                    description = stringResource(R.string.warehouse_bins_card_desc),
                    onClick = onBins,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_part_issues_card),
                    description = stringResource(R.string.warehouse_part_issues_card_desc),
                    onClick = onPartIssues,
                )
            }
            item {
                WarehouseActionCard(
                    title = stringResource(R.string.warehouse_transfer_requests_card),
                    description = stringResource(R.string.warehouse_transfer_requests_card_desc),
                    onClick = onTransferRequests,
                )
            }
        }
    }
}

@Composable
private fun WarehouseActionCard(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = description?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            },
        )
    }
}
