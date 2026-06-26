package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.core.network.model.CreateRentalRequest
import com.avago.feature.assets.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RATE_UNITS = listOf("hourly", "daily", "weekly", "monthly")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRentalSheet(
    assetId: String,
    currencyCode: String,
    onDismiss: () -> Unit,
    onConfirm: (CreateRentalRequest) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var customerName by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var rateUnitExpanded by remember { mutableStateOf(false) }
    var selectedRateUnit by remember { mutableStateOf("daily") }
    var notes by remember { mutableStateOf("") }

    // Start date — defaults to today
    val today = LocalDate.now()
    var startDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }

    var rateError by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

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
            Text(
                text = stringResource(R.string.rental_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            // Customer Name (optional)
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text(stringResource(R.string.rental_customer_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            // Rate (required)
            OutlinedTextField(
                value = rate,
                onValueChange = {
                    rate = it
                    rateError = false
                },
                label = { Text(stringResource(R.string.rental_rate_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = rateError,
                prefix = { Text(currencyCode) },
                supportingText = if (rateError) {
                    { Text(stringResource(R.string.rental_rate_label) + " is required") }
                } else null,
            )
            Spacer(Modifier.height(12.dp))

            // Rate Unit dropdown
            ExposedDropdownMenuBox(
                expanded = rateUnitExpanded,
                onExpandedChange = { rateUnitExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = rateUnitDisplayName(selectedRateUnit),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.rental_rate_unit_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rateUnitExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = rateUnitExpanded,
                    onDismissRequest = { rateUnitExpanded = false },
                ) {
                    RATE_UNITS.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(rateUnitDisplayName(unit)) },
                            onClick = {
                                selectedRateUnit = unit
                                rateUnitExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Start date
            OutlinedTextField(
                value = startDate.format(dateFormatter),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.rental_start_date_label)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Notes (optional, multiline)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.rental_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )
            Spacer(Modifier.height(24.dp))

            // Action buttons
            Button(
                onClick = {
                    val parsedRate = rate.toDoubleOrNull()
                    if (parsedRate == null || parsedRate <= 0.0) {
                        rateError = true
                        return@Button
                    }
                    val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    onConfirm(
                        CreateRentalRequest(
                            asset_id = assetId,
                            start_at = startInstant.toString(),
                            rate = parsedRate,
                            rate_unit = selectedRateUnit,
                            currency = currencyCode,
                            customer_name = customerName.trim().takeIf { it.isNotBlank() },
                            notes = notes.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.rental_create_button))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.asset_cancel))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.asset_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun rateUnitDisplayName(unit: String): String = when (unit) {
    "hourly" -> stringResource(R.string.rental_rate_unit_hourly)
    "daily" -> stringResource(R.string.rental_rate_unit_daily)
    "weekly" -> stringResource(R.string.rental_rate_unit_weekly)
    "monthly" -> stringResource(R.string.rental_rate_unit_monthly)
    else -> unit.replaceFirstChar { it.uppercase() }
}
