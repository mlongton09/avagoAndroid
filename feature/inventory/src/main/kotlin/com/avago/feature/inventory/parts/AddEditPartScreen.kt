package com.avago.feature.inventory.parts

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import com.avago.feature.inventory.ui.LocationPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPartScreen(
    partId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onPickCategory: (currentCategory: String?) -> Unit = {},
    pendingCategory: String? = null,
    viewModel: AddEditPartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLocationPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    LaunchedEffect(pendingCategory) {
        if (pendingCategory != null) viewModel.setCategory(pendingCategory)
    }

    if (showLocationPicker) {
        LocationPickerSheet(
            onLocationPicked = { location ->
                viewModel.setLocation(location)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (partId == null) R.string.add_part_title else R.string.edit_part_title,
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
            // Name (required)
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.part_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            // Manufacturer
            OutlinedTextField(
                value = state.manufacturer,
                onValueChange = viewModel::setManufacturer,
                label = { Text(stringResource(R.string.part_manufacturer_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            if (state.isEdit) {
                OutlinedTextField(
                    value = state.modelNumber,
                    onValueChange = viewModel::setModelNumber,
                    label = { Text(stringResource(R.string.part_model_number_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.revision,
                        onValueChange = viewModel::setRevision,
                        label = { Text(stringResource(R.string.part_revision_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.originCountryCode,
                        onValueChange = viewModel::setOriginCountryCode,
                        label = { Text(stringResource(R.string.part_origin_country_label)) },
                        supportingText = { Text(stringResource(R.string.part_origin_country_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            }

            OutlinedTextField(
                value = state.sku,
                onValueChange = viewModel::setSku,
                label = { Text(stringResource(R.string.part_sku_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Button }
                    .clickable { onPickCategory(state.category.takeIf { it.isNotBlank() }) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.part_category),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.category.ifBlank { stringResource(R.string.inventory_category_picker_none) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.unitCost,
                    onValueChange = viewModel::setUnitCost,
                    label = { Text(stringResource(R.string.part_unit_cost_label)) },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = viewModel::setCurrency,
                    label = { Text(stringResource(R.string.part_currency_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.reorderQty,
                    onValueChange = viewModel::setReorderQty,
                    label = { Text(stringResource(R.string.part_reorder_qty_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OptionDropdown(
                    label = stringResource(R.string.part_abc_class_label),
                    selected = state.abcClass,
                    options = listOf("") + ABC_CLASSES,
                    display = { it.ifBlank { "\u2014" } },
                    onSelected = viewModel::setAbcClass,
                    modifier = Modifier.weight(1f),
                )
            }

            // Unit of Measure dropdown
            OptionDropdown(
                label = stringResource(R.string.part_uom_label),
                selected = state.uom,
                options = UOM_OPTIONS,
                display = { it },
                onSelected = viewModel::setUom,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.barcode,
                onValueChange = viewModel::setBarcode,
                label = { Text(stringResource(R.string.part_barcode_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.part_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            if (partId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.part_archived_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.archived, onCheckedChange = viewModel::setArchived)
                }
            }

            // Initial Quantity — only for new parts
            if (partId == null) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Button }
                        .clickable { showLocationPicker = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.part_initial_location),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.locationName ?: stringResource(R.string.part_initial_location_none),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.initialQty,
                    onValueChange = viewModel::setInitialQty,
                    label = { Text(stringResource(R.string.part_initial_qty_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.common_loading)
                    else stringResource(R.string.part_save),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdown(
    label: String,
    selected: String,
    options: List<String>,
    display: (String) -> String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
