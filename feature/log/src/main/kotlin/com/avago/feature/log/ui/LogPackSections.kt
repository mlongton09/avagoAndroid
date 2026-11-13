package com.avago.feature.log.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.feature.log.R
import com.avago.feature.log.model.LogPacks
import com.avago.feature.log.viewmodel.AddEditLogFormState
import com.avago.feature.log.viewmodel.AddEditLogViewModel

/** Enum value → display label ("full_synthetic" → "Full synthetic"). */
private fun humanize(v: String): String =
    v.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Fuel card (contract §1): volume (required), unit, price/unit, grade, station, full fill, economy. */
@Composable
internal fun FuelPackSection(form: AddEditLogFormState, viewModel: AddEditLogViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.fuelVolume,
                onValueChange = viewModel::onFuelVolumeChanged,
                label = { Text(stringResource(R.string.log_pack_fuel_volume)) },
                supportingText = if (form.fuelDerived == "volume") {
                    { Text(stringResource(R.string.log_pack_fuel_calculated)) }
                } else null,
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            val units = listOf("gallon", "liter", "kWh")
            SingleChoiceSegmentedButtonRow {
                units.forEachIndexed { idx, unit ->
                    SegmentedButton(
                        selected = form.fuelVolumeUnit.equals(unit, ignoreCase = true),
                        onClick = { viewModel.onFuelVolumeUnitChanged(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = units.size),
                        label = {
                            Text(
                                when (unit) {
                                    "gallon" -> stringResource(R.string.log_entry_fuel_unit_gallon)
                                    "liter" -> stringResource(R.string.log_entry_fuel_unit_liter)
                                    else -> stringResource(R.string.log_pack_fuel_unit_kwh)
                                }
                            )
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = form.fuelPrice,
            onValueChange = viewModel::onFuelPriceChanged,
            label = { Text(stringResource(R.string.log_pack_fuel_price)) },
            supportingText = {
                Text(
                    if (form.fuelDerived == "price") stringResource(R.string.log_pack_fuel_calculated)
                    else stringResource(R.string.log_pack_fuel_any_two_hint)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (form.fuelDerived == "total") {
            Text(
                stringResource(R.string.log_pack_fuel_total_calculated, form.totalCost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EnumDropdown(
            label = stringResource(R.string.log_pack_fuel_grade),
            value = form.packFields["fuel_grade"].orEmpty(),
            options = LogPacks.FUEL_GRADES,
            onSelected = { viewModel.onPackFieldChanged("fuel_grade", it) },
        )
        PackText(form, viewModel, "fuel_station", R.string.log_pack_fuel_station)
        PackText(form, viewModel, "fuel_card_last4", R.string.log_pack_fuel_card_last4, KeyboardType.Number)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.log_pack_fuel_full_fill), modifier = Modifier.weight(1f))
            Switch(checked = form.fuelFullFill, onCheckedChange = viewModel::onFuelFullFillChanged)
        }
        val econ = form.fuelEconomyPreview
        Text(
            text = if (econ != null && form.fuelEconomyUnit != null) {
                stringResource(R.string.log_pack_fuel_economy, LogPacks.formatNumber(econ), form.fuelEconomyUnit)
            } else {
                stringResource(R.string.log_pack_fuel_economy_unavailable)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Oil / tire / battery / brake packs for the current category (contract §1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaintenancePackSections(form: AddEditLogFormState, viewModel: AddEditLogViewModel) {
    val packs = form.packs
    if (LogPacks.Pack.OIL in packs) {
        PackCard(stringResource(R.string.log_pack_oil_title)) {
            EnumDropdown(stringResource(R.string.log_pack_oil_type), form.packFields["oil_type"].orEmpty(), LogPacks.OIL_TYPES) {
                viewModel.onPackFieldChanged("oil_type", it)
            }
            PackText(form, viewModel, "oil_viscosity", R.string.log_pack_oil_viscosity)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackText(form, viewModel, "oil_quantity", R.string.log_pack_oil_quantity, KeyboardType.Decimal, Modifier.weight(1f))
                EnumDropdown(
                    stringResource(R.string.log_pack_unit), form.packFields["oil_quantity_unit"].orEmpty(),
                    LogPacks.OIL_QTY_UNITS, Modifier.weight(1f),
                ) { viewModel.onPackFieldChanged("oil_quantity_unit", it) }
            }
            PackText(form, viewModel, "oil_filter_part", R.string.log_pack_oil_filter_part)
            PackText(form, viewModel, "oil_next_due_meter", R.string.log_pack_oil_next_due_meter, KeyboardType.Decimal)
            PackText(form, viewModel, "oil_next_due_date", R.string.log_pack_oil_next_due_date, hint = "YYYY-MM-DD")
        }
    }
    if (LogPacks.Pack.TIRE in packs) {
        PackCard(stringResource(R.string.log_pack_tire_title)) {
            EnumDropdown(stringResource(R.string.log_pack_action), form.packFields["tire_action"].orEmpty(), LogPacks.TIRE_ACTIONS) {
                viewModel.onPackFieldChanged("tire_action", it)
            }
            Text(stringResource(R.string.log_pack_tire_positions), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LogPacks.TIRE_POSITIONS.forEach { pos ->
                    FilterChip(
                        selected = pos in form.tirePositions,
                        onClick = { viewModel.onTirePositionToggled(pos) },
                        label = { Text(pos.uppercase()) },
                    )
                }
            }
            PackText(form, viewModel, "tire_brand", R.string.log_pack_tire_brand)
            PackText(form, viewModel, "tire_size", R.string.log_pack_tire_size)
            PackText(form, viewModel, "tire_dot_date", R.string.log_pack_tire_dot_date, KeyboardType.Number, hint = "WWYY")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnumDropdown(
                    stringResource(R.string.log_pack_tire_tread_unit), form.packFields["tire_tread_unit"].orEmpty(),
                    LogPacks.TIRE_TREAD_UNITS, Modifier.weight(1f),
                ) { viewModel.onPackFieldChanged("tire_tread_unit", it) }
                EnumDropdown(
                    stringResource(R.string.log_pack_tire_pressure_unit), form.packFields["tire_pressure_unit"].orEmpty(),
                    LogPacks.TIRE_PRESSURE_UNITS, Modifier.weight(1f),
                ) { viewModel.onPackFieldChanged("tire_pressure_unit", it) }
            }
            LogPacks.TIRE_POSITIONS.filter { it in form.tirePositions }.forEach { pos ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pos.uppercase(), modifier = Modifier.width(44.dp))
                    PositionValue(form, viewModel, "tire_tread", pos, R.string.log_pack_tire_tread, Modifier.weight(1f))
                    PositionValue(form, viewModel, "tire_psi", pos, R.string.log_pack_tire_psi, Modifier.weight(1f))
                }
            }
        }
    }
    if (LogPacks.Pack.BATTERY in packs) {
        PackCard(stringResource(R.string.log_pack_battery_title)) {
            EnumDropdown(stringResource(R.string.log_pack_action), form.packFields["battery_action"].orEmpty(), LogPacks.BATTERY_ACTIONS) {
                viewModel.onPackFieldChanged("battery_action", it)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackText(form, viewModel, "battery_rated_cca", R.string.log_pack_battery_rated_cca, KeyboardType.Number, Modifier.weight(1f))
                PackText(form, viewModel, "battery_measured_cca", R.string.log_pack_battery_measured_cca, KeyboardType.Number, Modifier.weight(1f))
            }
            PackText(form, viewModel, "battery_voltage", R.string.log_pack_battery_voltage, KeyboardType.Decimal)
            EnumDropdown(stringResource(R.string.log_pack_battery_result), form.packFields["battery_test_result"].orEmpty(), LogPacks.BATTERY_RESULTS) {
                viewModel.onPackFieldChanged("battery_test_result", it)
            }
            PackText(form, viewModel, "battery_date_code", R.string.log_pack_battery_date_code)
        }
    }
    if (LogPacks.Pack.BRAKE in packs) {
        PackCard(stringResource(R.string.log_pack_brake_title)) {
            EnumDropdown(stringResource(R.string.log_pack_brake_axle), form.packFields["brake_axle"].orEmpty(), LogPacks.BRAKE_AXLES) {
                viewModel.onPackFieldChanged("brake_axle", it)
            }
            EnumDropdown(stringResource(R.string.log_pack_brake_fluid), form.packFields["brake_fluid"].orEmpty(), LogPacks.BRAKE_FLUID) {
                viewModel.onPackFieldChanged("brake_fluid", it)
            }
            LogPacks.BRAKE_POSITIONS.forEach { pos ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pos.uppercase(), modifier = Modifier.width(44.dp))
                    PositionValue(form, viewModel, "brake_pad_mm", pos, R.string.log_pack_brake_pad_mm, Modifier.weight(1f))
                    PositionValue(form, viewModel, "brake_rotor_mm", pos, R.string.log_pack_brake_rotor_mm, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Vendor picker (known vendors + free text), work-order link and receipt scan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogVendorWoSection(
    form: AddEditLogFormState,
    viewModel: AddEditLogViewModel,
    onScanReceipt: () -> Unit,
) {
    PackCard(stringResource(R.string.log_pack_vendor_section)) {
        var vendorOpen by remember { mutableStateOf(false) }
        val matches = form.vendorOptions.filter {
            form.vendorName.isBlank() || it.name.contains(form.vendorName, ignoreCase = true)
        }.take(20)
        ExposedDropdownMenuBox(expanded = vendorOpen && matches.isNotEmpty(), onExpandedChange = { vendorOpen = it }) {
            OutlinedTextField(
                value = form.vendorName,
                onValueChange = { viewModel.onVendorNameChanged(it); vendorOpen = true },
                label = { Text(stringResource(R.string.log_pack_vendor)) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vendorOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(expanded = vendorOpen && matches.isNotEmpty(), onDismissRequest = { vendorOpen = false }) {
                matches.forEach { v ->
                    DropdownMenuItem(text = { Text(v.name) }, onClick = { viewModel.onVendorSelected(v); vendorOpen = false })
                }
            }
        }

        if (form.woOptions.isNotEmpty() || form.linkedWoId != null) {
            var woOpen by remember { mutableStateOf(false) }
            val none = stringResource(R.string.log_pack_wo_none)
            val selected = form.woOptions.firstOrNull { it.woId == form.linkedWoId }
            ExposedDropdownMenuBox(expanded = woOpen, onExpandedChange = { woOpen = it }) {
                OutlinedTextField(
                    value = selected?.title ?: form.linkedWoId ?: none,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.log_pack_wo_link)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = woOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = woOpen, onDismissRequest = { woOpen = false }) {
                    DropdownMenuItem(text = { Text(none) }, onClick = { viewModel.onLinkedWorkOrderSelected(null); woOpen = false })
                    form.woOptions.forEach { wo ->
                        DropdownMenuItem(
                            text = { Text(wo.title) },
                            onClick = { viewModel.onLinkedWorkOrderSelected(wo.woId); woOpen = false },
                        )
                    }
                }
            }
        }

        PackText(form, viewModel, "warranty_until", R.string.log_pack_warranty_until, hint = "YYYY-MM-DD")
        PackText(form, viewModel, "warranty_provider", R.string.log_pack_warranty_provider)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onScanReceipt, enabled = !form.receiptOcrRunning) {
                Text(stringResource(R.string.log_pack_scan_receipt))
            }
            if (form.receiptOcrRunning) CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            when (form.receiptOcrOutcome) {
                "applied" -> Text(stringResource(R.string.log_pack_receipt_applied), style = MaterialTheme.typography.bodySmall)
                "attached" -> Text(stringResource(R.string.log_pack_receipt_attached), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PackCard(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    FormCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun PackText(
    form: AddEditLogFormState,
    viewModel: AddEditLogViewModel,
    key: String,
    labelRes: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    hint: String? = null,
) {
    OutlinedTextField(
        value = form.packFields[key].orEmpty(),
        onValueChange = { viewModel.onPackFieldChanged(key, it) },
        label = { Text(stringResource(labelRes)) },
        placeholder = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

@Composable
private fun PositionValue(
    form: AddEditLogFormState,
    viewModel: AddEditLogViewModel,
    mapKey: String,
    position: String,
    labelRes: Int,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = form.packMaps[mapKey]?.get(position).orEmpty(),
        onValueChange = { viewModel.onPackMapValueChanged(mapKey, position, it) },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else humanize(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("—") }, onClick = { onSelected(""); open = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(humanize(opt)) }, onClick = { onSelected(opt); open = false })
            }
        }
    }
}
