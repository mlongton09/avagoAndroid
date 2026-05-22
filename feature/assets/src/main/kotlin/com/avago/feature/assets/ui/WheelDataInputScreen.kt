package com.avago.feature.assets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.feature.assets.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CONDITIONS = listOf("Good", "Fair", "Poor", "Replace")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelDataInputScreen(
    onSave: (
        treadDepthMm: String,
        tirePressurePsi: String,
        lastInspectionMs: Long?,
        nextInspectionMs: Long?,
        condition: String,
    ) -> Unit,
    onBack: () -> Unit,
) {
    var treadDepth by remember { mutableStateOf("") }
    var tirePressure by remember { mutableStateOf("") }
    var lastInspectionMs by remember { mutableStateOf<Long?>(null) }
    var nextInspectionMs by remember { mutableStateOf<Long?>(null) }
    var condition by remember { mutableStateOf(CONDITIONS[0]) }

    var showLastInspectionPicker by remember { mutableStateOf(false) }
    var showNextInspectionPicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    if (showLastInspectionPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = lastInspectionMs)
        DatePickerDialog(
            onDismissRequest = { showLastInspectionPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    lastInspectionMs = state.selectedDateMillis
                    showLastInspectionPicker = false
                }) { Text(stringResource(R.string.wheel_data_dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showLastInspectionPicker = false }) { Text(stringResource(R.string.wheel_data_dialog_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showNextInspectionPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = nextInspectionMs)
        DatePickerDialog(
            onDismissRequest = { showNextInspectionPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    nextInspectionMs = state.selectedDateMillis
                    showNextInspectionPicker = false
                }) { Text(stringResource(R.string.wheel_data_dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showNextInspectionPicker = false }) { Text(stringResource(R.string.wheel_data_dialog_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wheel_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = treadDepth,
                onValueChange = { treadDepth = it },
                label = { Text(stringResource(R.string.wheel_data_tread_depth_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = tirePressure,
                onValueChange = { tirePressure = it },
                label = { Text(stringResource(R.string.wheel_data_tire_pressure_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = lastInspectionMs?.let { dateFormatter.format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.wheel_data_last_inspection_label)) },
                trailingIcon = {
                    IconButton(onClick = { showLastInspectionPicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = nextInspectionMs?.let { dateFormatter.format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.wheel_data_next_inspection_label)) },
                trailingIcon = {
                    IconButton(onClick = { showNextInspectionPicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.wheel_data_condition_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CONDITIONS.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = condition == label,
                        onClick = { condition = label },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CONDITIONS.size),
                        label = { Text(label) },
                    )
                }
            }

            Button(
                onClick = { onSave(treadDepth, tirePressure, lastInspectionMs, nextInspectionMs, condition) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.wheel_data_save))
            }
        }
    }
}
