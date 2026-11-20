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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.StockMovement
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderDetailScreen(
    poId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    viewModel: PurchaseOrderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val po = state.po
    var reverseTarget by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.showGrnSheet && po != null) {
        GrnCreateSheet(po = po, onDismiss = viewModel::dismissGrnSheet)
    }

    state.confirmAction?.let { action ->
        PoConfirmDialog(
            action = action,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirm,
        )
    }

    reverseTarget?.let { receiptId ->
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            text = { Text(stringResource(R.string.po_receipt_reverse_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    reverseTarget = null
                    viewModel.reverseReceipt(receiptId)
                }) { Text(stringResource(R.string.po_receipt_reverse)) }
            },
            dismissButton = {
                TextButton(onClick = { reverseTarget = null }) { Text(stringResource(R.string.common_dismiss)) }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(po?.displayNumber ?: stringResource(R.string.po_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (po != null && po.status.isOpen) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.po_action_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (po == null) {
            EmptyState(
                message = stringResource(if (state.isLoading) R.string.common_loading else R.string.po_empty),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PoHeaderCard(po) }
            item { PoTotalsCard(po) }

            item {
                Text(text = stringResource(R.string.po_line_items), style = MaterialTheme.typography.titleSmall)
            }
            items(po.lines, key = { it.id }) { line ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            line.description ?: line.productName ?: line.supplierSku ?: "#${line.lineNo}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.po_received_of, line.receivedQty.plain(), line.orderedQty.plain()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(formatPoMoney(line.currency, line.lineTotal), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (state.receipts.isNotEmpty()) {
                item {
                    Text(text = stringResource(R.string.po_receipts_section), style = MaterialTheme.typography.titleSmall)
                }
                items(state.receipts, key = { it.id }) { receipt ->
                    ReceiptRow(receipt = receipt, onReverse = { reverseTarget = receipt.id })
                }
            }

            item {
                PoActions(
                    po = po,
                    actionError = state.actionError,
                    onSubmit = viewModel::submit,
                    onApprove = viewModel::approve,
                    onReject = { viewModel.requestConfirm(PoConfirmAction.REJECT) },
                    onMarkOrdered = viewModel::markOrdered,
                    onReceive = viewModel::openGrnSheet,
                    onClose = viewModel::close,
                    onCancel = { viewModel.requestConfirm(PoConfirmAction.CANCEL) },
                )
            }
        }
    }
}

@Composable
private fun PoHeaderCard(po: PurchaseOrder) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(po.displayNumber, style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = {}, label = { Text(poStatusLabel(po.status)) })
            }
            po.supplierName?.let {
                Spacer(Modifier.height(4.dp))
                PoDetailRow(stringResource(R.string.po_vendor_label), it)
            }
            po.expectedOn?.let { PoDetailRow(stringResource(R.string.po_expected_delivery), it) }
            po.supplierInvoiceRef?.let { PoDetailRow(stringResource(R.string.po_invoice_ref_label), it) }
            po.notes?.let { PoDetailRow(stringResource(R.string.po_notes), it) }
            po.internalNotes?.let { PoDetailRow(stringResource(R.string.po_internal_notes_label), it) }
        }
    }
}

@Composable
private fun PoTotalsCard(po: PurchaseOrder) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            PoDetailRow(stringResource(R.string.po_subtotal), formatPoMoney(po.currency, po.subtotal))
            PoDetailRow(stringResource(R.string.po_tax), formatPoMoney(po.currency, po.taxTotal))
            if (po.shipping.signum() != 0) {
                PoDetailRow(stringResource(R.string.po_shipping), formatPoMoney(po.currency, po.shipping))
            }
            if (po.discount.signum() != 0) {
                PoDetailRow(stringResource(R.string.po_discount), "- " + formatPoMoney(po.currency, po.discount))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            PoDetailRow(stringResource(R.string.po_grand_total), formatPoMoney(po.currency, po.grandTotal))
        }
    }
}

@Composable
private fun ReceiptRow(receipt: StockMovement, onReverse: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(receipt.number ?: receipt.id.take(8), style = MaterialTheme.typography.bodyMedium)
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(receipt.effectiveAt ?: receipt.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                receipt.reversedById != null -> Text(
                    stringResource(R.string.po_receipt_reversed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                receipt.isReversible -> TextButton(onClick = onReverse) {
                    Text(stringResource(R.string.po_receipt_reverse))
                }
            }
        }
    }
}

@Composable
private fun PoActions(
    po: PurchaseOrder,
    actionError: String?,
    onSubmit: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onMarkOrdered: () -> Unit,
    onReceive: () -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (po.canSubmit) {
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.po_action_submit)) }
        }
        if (po.canApprove) {
            Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.po_action_approve)) }
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.po_action_reject)) }
        }
        if (po.canMarkOrdered) {
            Button(onClick = onMarkOrdered, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.po_action_mark_ordered))
            }
        }
        if (po.canReceive) {
            Button(onClick = onReceive, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.po_action_receive)) }
        }
        if (po.canClose) {
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.po_action_close))
            }
        }
        if (po.canCancel) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.po_action_cancel)) }
        }
    }
}

@Composable
private fun PoConfirmDialog(action: PoConfirmAction, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (action) {
                        PoConfirmAction.REJECT -> R.string.po_reason_reject_title
                        PoConfirmAction.CANCEL -> R.string.po_reason_cancel_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        when (action) {
                            PoConfirmAction.REJECT -> R.string.po_confirm_reject_message
                            PoConfirmAction.CANCEL -> R.string.po_confirm_cancel_message
                        },
                    ),
                )
                if (action == PoConfirmAction.REJECT) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.po_reject_reason_label)) },
                        supportingText = { Text(stringResource(R.string.po_reject_reason_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim().ifEmpty { null }) }) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_dismiss)) } },
    )
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

private fun java.math.BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
