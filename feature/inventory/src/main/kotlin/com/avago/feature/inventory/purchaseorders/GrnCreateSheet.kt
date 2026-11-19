package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.PurchaseOrder
import com.avago.feature.inventory.ui.LocationPickerSheet
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrnCreateSheet(
    po: PurchaseOrder,
    onDismiss: () -> Unit,
    viewModel: GrnViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(po.id) { viewModel.init(po) }
    LaunchedEffect(state.isDone) { if (state.isDone) onDismiss() }

    if (state.showLocationPicker) {
        LocationPickerSheet(
            onLocationPicked = { location -> viewModel.setLocation(location?.id, location?.name) },
            onDismiss = { viewModel.showLocationPicker(false) },
            showClearOption = false,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.grn_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            ListItem(
                overlineContent = { Text(stringResource(R.string.grn_location_label)) },
                headlineContent = {
                    Text(state.receivingLocationName ?: stringResource(R.string.po_select_location))
                },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.showLocationPicker(true) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.carrier,
                onValueChange = viewModel::setCarrier,
                label = { Text(stringResource(R.string.grn_carrier_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.trackingNumber,
                onValueChange = viewModel::setTrackingNumber,
                label = { Text(stringResource(R.string.grn_tracking_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.packingSlipNo,
                onValueChange = viewModel::setPackingSlipNo,
                label = { Text(stringResource(R.string.grn_packing_slip_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.grn_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            state.lines.forEach { line ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = line.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(
                                    R.string.grn_outstanding,
                                    line.outstandingQty.stripTrailingZeros().toPlainString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = line.receivedQty,
                            onValueChange = { viewModel.setLineQty(line.poLineId, it) },
                            label = { Text(stringResource(R.string.grn_qty_received_label)) },
                            modifier = Modifier.weight(0.5f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    val short = line.receivedQty.trim().toBigDecimalOrNull()?.let { it.compareTo(line.outstandingQty) != 0 } == true
                    if (short) {
                        OutlinedTextField(
                            value = line.discrepancyReason,
                            onValueChange = { viewModel.setLineDiscrepancy(line.poLineId, it) },
                            label = { Text(stringResource(R.string.grn_discrepancy_reason_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submit(po.id) },
                enabled = !state.isSubmitting && state.lines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) stringResource(R.string.grn_submitting)
                    else stringResource(R.string.grn_submit),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
