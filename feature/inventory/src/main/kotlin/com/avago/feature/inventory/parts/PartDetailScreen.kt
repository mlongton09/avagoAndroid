package com.avago.feature.inventory.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.InventoryTransactionEntity
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartDetailScreen(
    partId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    viewModel: PartDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showReceiveSheet && state.inventory != null) {
        ReceiveUseModalSheet(
            inventoryId = state.inventory!!.inventoryId,
            mode = ReceiveUseMode.RECEIVE,
            onDismiss = viewModel::dismissSheet,
        )
    }
    if (state.showUseSheet && state.inventory != null) {
        ReceiveUseModalSheet(
            inventoryId = state.inventory!!.inventoryId,
            mode = ReceiveUseMode.USE,
            onDismiss = viewModel::dismissSheet,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.part?.name ?: stringResource(R.string.part_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.part_edit))
                    }
                },
            )
        },
    ) { padding ->
        val part = state.part
        if (part == null) {
            EmptyState(
                message = stringResource(R.string.common_loading),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Part header card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        part.sku?.let { LabelValue(stringResource(R.string.part_sku), it) }
                        part.category?.let { LabelValue(stringResource(R.string.part_category), it) }
                        part.description?.let { LabelValue("Description", it) }
                    }
                }
            }

            item {
                // Stock stats card
                val inv = state.inventory
                val sl = state.stockingLevel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.part_stock_stats),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        LabelValue(
                            stringResource(R.string.part_on_hand),
                            "%.2f".format(inv?.quantityOnHand ?: 0.0),
                        )
                        sl?.minQty?.let { LabelValue(stringResource(R.string.part_min_qty), "%.2f".format(it)) }
                        sl?.maxQty?.let { LabelValue(stringResource(R.string.part_max_qty), "%.2f".format(it)) }
                        sl?.reorderQty?.let { LabelValue(stringResource(R.string.part_reorder_point), "%.2f".format(it)) }
                        sl?.safetyStock?.let { LabelValue("Safety Stock", "%.2f".format(it)) }
                    }
                }
            }

            item {
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::openReceive,
                        modifier = Modifier.weight(1f),
                        enabled = state.inventory != null,
                    ) {
                        Text(stringResource(R.string.part_receive))
                    }
                    OutlinedButton(
                        onClick = viewModel::openUse,
                        modifier = Modifier.weight(1f),
                        enabled = state.inventory != null,
                    ) {
                        Text(stringResource(R.string.part_use))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.part_transaction_ledger),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.part_no_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.transactions, key = { it.transactionId }) { txn ->
                    TransactionRow(txn)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TransactionRow(txn: InventoryTransactionEntity) {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val date = fmt.format(Date(txn.createdAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(txn.transactionType, style = MaterialTheme.typography.bodyMedium)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sign = if (txn.quantity >= 0) "+" else ""
        Text(
            text = "$sign%.2f".format(txn.quantity),
            style = MaterialTheme.typography.bodyMedium,
            color = if (txn.quantity >= 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
}
