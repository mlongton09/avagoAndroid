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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import com.avago.feature.inventory.parts.ProductPickerSheet
import com.avago.feature.inventory.ui.LocationPickerSheet
import com.avago.feature.inventory.vendors.VendorPickerSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

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
                if (vendor != null) viewModel.setVendor(vendor)
                else viewModel.dismissVendorPicker()
            },
            onDismiss = viewModel::dismissVendorPicker,
        )
    }

    if (state.showLocationPicker) {
        LocationPickerSheet(
            onLocationPicked = { location -> viewModel.onLocationSelected(location?.id, location?.name) },
            onDismiss = viewModel::dismissLocationPicker,
        )
    }

    if (state.productPickerLine != null) {
        ProductPickerSheet(
            onProductSelected = viewModel::onProductSelected,
            onDismiss = { viewModel.pickProductFor(null) },
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
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.onDeliveryDateChanged(null)
                    showDatePicker = false
                }) { Text(stringResource(R.string.po_clear_date)) }
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
                enabled = state.isDraft,
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
                    SimpleDateFormat("MMM d, yyyy", LocalConfiguration.current.locales[0])
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .format(Date(state.expectedDeliveryMs!!))
                else "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.po_expected_delivery_label)) },
                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Button }
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
                    .clickable { viewModel.showLocationPicker() },
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.po_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            if (poId != null) {
                OutlinedTextField(
                    value = state.internalNotes,
                    onValueChange = viewModel::setInternalNotes,
                    label = { Text(stringResource(R.string.po_internal_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = state.supplierInvoiceRef,
                    onValueChange = viewModel::setSupplierInvoiceRef,
                    label = { Text(stringResource(R.string.po_invoice_ref_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Costs section
            Text(stringResource(R.string.po_costs_section), style = MaterialTheme.typography.titleSmall)

            // Currency dropdown
            ExposedDropdownMenuBox(
                expanded = currencyExpanded && state.isDraft,
                onExpandedChange = { currencyExpanded = it && state.isDraft },
            ) {
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = {},
                    readOnly = true,
                    enabled = state.isDraft,
                    label = { Text(stringResource(R.string.po_currency_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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

            if (poId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.shipping,
                        onValueChange = viewModel::setShipping,
                        label = { Text(stringResource(R.string.po_shipping_cost_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.discount,
                        onValueChange = viewModel::setDiscount,
                        label = { Text(stringResource(R.string.po_discount_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Line items
            Text(stringResource(R.string.po_line_items), style = MaterialTheme.typography.titleSmall)
            state.lines.forEachIndexed { lineIndex, line ->
                if (!state.isDraft) return@forEachIndexed
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.po_line_item_number, lineIndex + 1),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.removeLine(line.key) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.po_line_remove))
                            }
                        }

                        // Part picker field
                        OutlinedTextField(
                            value = line.productName.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.po_line_part_optional)) },
                            placeholder = { Text(stringResource(R.string.po_line_part_placeholder)) },
                            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { role = Role.Button }
                                .clickable { viewModel.pickProductFor(lineIndex) },
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
                            OutlinedTextField(
                                value = line.taxRatePct,
                                onValueChange = { viewModel.updateLine(line.copy(taxRatePct = it)) },
                                label = { Text(stringResource(R.string.po_line_tax_pct)) },
                                modifier = Modifier.weight(0.7f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            }

            if (state.isDraft) OutlinedButton(
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
