package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import com.avago.feature.inventory.vendors.VendorPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderCreateScreen(
    poId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: PurchaseOrderCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    if (state.showVendorPicker) {
        VendorPickerSheet(
            onVendorSelected = { vendor ->
                if (vendor != null) viewModel.setVendor(vendor.vendorId, vendor.name)
                else viewModel.dismissVendorPicker()
            },
            onDismiss = viewModel::dismissVendorPicker,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (poId == null) R.string.po_create_title else R.string.po_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Vendor picker
            OutlinedButton(
                onClick = viewModel::showVendorPicker,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.vendorName.isNotBlank()) state.vendorName
                    else stringResource(R.string.po_select_vendor),
                )
            }

            OutlinedTextField(
                value = state.expectedDelivery,
                onValueChange = viewModel::setExpectedDelivery,
                label = { Text(stringResource(R.string.po_expected_delivery_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.po_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            // Cost approval
            Text(stringResource(R.string.po_cost_approval_label), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.costApproval == "not_required",
                    onClick = { viewModel.setCostApproval("not_required") },
                )
                Text(stringResource(R.string.po_cost_approval_not_required))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.costApproval == "required",
                    onClick = { viewModel.setCostApproval("required") },
                )
                Text(stringResource(R.string.po_cost_approval_required))
            }

            // Line items
            Text(stringResource(R.string.po_line_items), style = MaterialTheme.typography.titleSmall)
            state.lines.forEach { line ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = line.description,
                                onValueChange = { viewModel.updateLine(line.copy(description = it)) },
                                label = { Text(stringResource(R.string.po_line_part)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            IconButton(onClick = { viewModel.removeLine(line.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.po_line_remove))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = line.quantity,
                                onValueChange = { viewModel.updateLine(line.copy(quantity = it)) },
                                label = { Text(stringResource(R.string.po_line_qty)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            OutlinedTextField(
                                value = line.unitCost,
                                onValueChange = { viewModel.updateLine(line.copy(unitCost = it)) },
                                label = { Text(stringResource(R.string.po_line_unit_cost)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::addLine,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.po_add_line_item))
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.po_submitting)
                    else stringResource(R.string.po_save),
                )
            }
        }
    }
}
