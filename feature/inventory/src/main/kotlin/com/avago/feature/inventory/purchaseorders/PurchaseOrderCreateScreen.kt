package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import com.avago.feature.inventory.vendors.VendorPickerSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderCreateScreen(
    poId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onPickLocation: () -> Unit = {},
    onPickPart: ((lineIndex: Int) -> Unit)? = null,
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

    // Date picker dialog
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.expectedDeliveryMs ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDeliveryDateChanged(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    // Currency dropdown state
    var currencyExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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

            // Expected delivery — date picker
            OutlinedTextField(
                value = if (state.expectedDeliveryMs != null)
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(state.expectedDeliveryMs!!))
                else "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.po_expected_delivery_label)) },
                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
            )

            // Ship-to location picker
            ListItem(
                headlineContent = {
                    Text(
                        state.shipToLocationName ?: stringResource(R.string.po_select_location),
                        style = if (state.shipToLocationName != null)
                            MaterialTheme.typography.bodyLarge
                        else
                            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                },
                overlineContent = { Text(stringResource(R.string.po_ship_to_location)) },
                leadingContent = {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickLocation() },
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.po_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            // Costs section
            Text(stringResource(R.string.po_costs_section), style = MaterialTheme.typography.titleSmall)

            // Currency dropdown
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.po_currency_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false },
                ) {
                    CURRENCIES.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = {
                                viewModel.onCurrencyChanged(code)
                                currencyExpanded = false
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.shippingCost,
                    onValueChange = viewModel::onShippingCostChanged,
                    label = { Text(stringResource(R.string.po_shipping_cost_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = state.discountAmount,
                    onValueChange = viewModel::onDiscountAmountChanged,
                    label = { Text(stringResource(R.string.po_discount_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

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
            state.lines.forEachIndexed { lineIndex, line ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.po_line_item_number, lineIndex + 1),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.removeLine(line.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.po_line_remove))
                            }
                        }

                        // Part picker field
                        OutlinedTextField(
                            value = line.partName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.po_line_part_optional)) },
                            placeholder = { Text(stringResource(R.string.po_line_part_placeholder)) },
                            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickPart?.invoke(lineIndex) },
                        )

                        OutlinedTextField(
                            value = line.description,
                            onValueChange = { viewModel.updateLine(line.copy(description = it)) },
                            label = { Text(stringResource(R.string.po_line_part)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

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
