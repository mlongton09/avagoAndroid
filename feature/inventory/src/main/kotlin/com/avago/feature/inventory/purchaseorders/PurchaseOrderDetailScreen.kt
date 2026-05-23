package com.avago.feature.inventory.purchaseorders

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderDetailScreen(
    poId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    viewModel: PurchaseOrderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showGrnSheet) {
        GrnCreateSheet(
            poId = poId,
            lines = state.lines,
            onDismiss = viewModel::dismissGrnSheet,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.po?.poNumber ?: stringResource(R.string.po_detail_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.po?.status == "draft") {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.po_action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val po = state.po
        if (po == null) {
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
                // Header card
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(po.poNumber ?: po.poId.take(8), style = MaterialTheme.typography.titleMedium)
                            AssistChip(
                                onClick = {},
                                label = { Text(poStatusLabel(po.status)) },
                            )
                        }
                        state.vendor?.let { vendor ->
                            Spacer(Modifier.height(4.dp))
                            PoDetailRow(stringResource(R.string.po_vendor_label), vendor.name)
                        }
                        po.expectedDelivery?.let { PoDetailRow(stringResource(R.string.po_expected_delivery), it) }
                        po.notes?.let { PoDetailRow(stringResource(R.string.po_notes), it) }
                    }
                }
            }

            item {
                // Cost breakdown card
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        po.subtotal?.let { PoDetailRow(stringResource(R.string.po_subtotal), "${po.currency ?: "USD"} ${"%.2f".format(it)}") }
                        po.taxTotal?.let { PoDetailRow(stringResource(R.string.po_tax), "${po.currency ?: "USD"} ${"%.2f".format(it)}") }
                        po.shippingCost?.let { PoDetailRow(stringResource(R.string.po_shipping), "${po.currency ?: "USD"} ${"%.2f".format(it)}") }
                        po.discount?.let { PoDetailRow(stringResource(R.string.po_discount), "- ${po.currency ?: "USD"} ${"%.2f".format(it)}") }
                        po.grandTotal?.let {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            PoDetailRow(stringResource(R.string.po_grand_total), "${po.currency ?: "USD"} ${"%.2f".format(it)}")
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.po_line_items),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            items(state.lines, key = { it.poLineId }) { line ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(line.description ?: line.partId ?: "—", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Qty: ${"%.2f".format(line.quantity)}", style = MaterialTheme.typography.bodySmall)
                            line.unitCost?.let {
                                Text(
                                    "${line.currency ?: "USD"} ${"%.2f".format(it * line.quantity)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Action buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.actionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    when (po.status) {
                        "draft" -> {
                            Button(
                                onClick = viewModel::submit,
                                enabled = !state.isActioning,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.po_action_submit)) }
                        }
                        "pending_approval" -> {
                            Button(
                                onClick = viewModel::approve,
                                enabled = !state.isActioning,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.po_action_approve)) }
                            OutlinedButton(
                                onClick = viewModel::reject,
                                enabled = !state.isActioning,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text(stringResource(R.string.po_action_reject)) }
                        }
                        "approved" -> Button(
                            onClick = viewModel::markOrdered,
                            enabled = !state.isActioning,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.po_action_mark_ordered)) }
                        "ordered", "partially_received" -> Button(
                            onClick = viewModel::openGrnSheet,
                            enabled = !state.isActioning,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.po_action_receive)) }
                        "received" -> OutlinedButton(
                            onClick = viewModel::close,
                            enabled = !state.isActioning,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.po_action_close)) }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun PoDetailRow(label: String, value: String) {
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
