package com.avago.feature.schedule.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.schedule.R
import com.avago.feature.schedule.util.ScheduleFrequencyPreset
import com.avago.feature.schedule.viewmodel.AddEditScheduleViewModel
import com.avago.feature.schedule.viewmodel.ScheduleTypeSelection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone

private val METER_TYPES = listOf("odometer", "km", "hours", "cycles")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScheduleScreen(
    scheduleId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPickAsset: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditScheduleViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val assetName by viewModel.assetName.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val scheduleType by viewModel.scheduleType.collectAsStateWithLifecycle()
    val frequencyPreset by viewModel.frequencyPreset.collectAsStateWithLifecycle()
    val meterType by viewModel.meterType.collectAsStateWithLifecycle()
    val meterInterval by viewModel.meterInterval.collectAsStateWithLifecycle()
    val meterCurrent by viewModel.meterCurrent.collectAsStateWithLifecycle()
    val endType by viewModel.endType.collectAsStateWithLifecycle()
    val endDate by viewModel.endDate.collectAsStateWithLifecycle()
    val endCount by viewModel.endCount.collectAsStateWithLifecycle()
    val timezone by viewModel.timezone.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val titleError by viewModel.titleError.collectAsStateWithLifecycle()
    val assetError by viewModel.assetError.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val savedSuccessfully by viewModel.savedSuccessfully.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(savedSuccessfully) {
        if (savedSuccessfully) onSaved()
    }

    LaunchedEffect(error) {
        error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    var showFrequencyMenu by remember { mutableStateOf(false) }
    var showMeterTypeMenu by remember { mutableStateOf(false) }
    var showTimezoneMenu by remember { mutableStateOf(false) }
    val endTypes = listOf("never" to "Never", "date" to "On Date", "count" to "After Count")

    val isEdit = scheduleId != null

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.schedule_cancel),
                        )
                    }
                },
                title = {
                    Text(
                        if (isEdit) stringResource(R.string.schedule_edit_title)
                        else stringResource(R.string.schedule_add_title)
                    )
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = viewModel::save) {
                            Text(stringResource(R.string.schedule_save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Title ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.title.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_field_title)) },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.schedule_field_title_required)) }
                } else null,
                singleLine = true,
            )

            // ── Asset picker ───────────────────────────────────────────────────
            OutlinedTextField(
                value = assetName.ifBlank { stringResource(R.string.schedule_field_asset_placeholder) },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickAsset() },
                label = { Text(stringResource(R.string.schedule_field_asset)) },
                isError = assetError,
                supportingText = if (assetError) {
                    { Text(stringResource(R.string.schedule_field_asset_required)) }
                } else null,
                enabled = false,
                readOnly = true,
            )

            // ── Category ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = category,
                onValueChange = { viewModel.category.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_field_category)) },
                placeholder = { Text(stringResource(R.string.schedule_field_category_placeholder)) },
                singleLine = true,
            )

            // ── Schedule type toggle ───────────────────────────────────────────
            Text(
                text = stringResource(R.string.schedule_field_type),
                style = MaterialTheme.typography.labelLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ScheduleTypeSelection.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = scheduleType == type,
                        onClick = { viewModel.scheduleType.value = type },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ScheduleTypeSelection.entries.size,
                        ),
                        label = {
                            Text(
                                if (type == ScheduleTypeSelection.BY_DATE)
                                    stringResource(R.string.schedule_field_type_date)
                                else
                                    stringResource(R.string.schedule_field_type_meter)
                            )
                        },
                    )
                }
            }

            // ── By-Date fields ─────────────────────────────────────────────────
            if (scheduleType == ScheduleTypeSelection.BY_DATE) {
                // Frequency picker
                OutlinedTextField(
                    value = frequencyPreset.displayName,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFrequencyMenu = true },
                    label = { Text(stringResource(R.string.schedule_field_frequency)) },
                    enabled = false,
                    readOnly = true,
                )
                DropdownMenu(
                    expanded = showFrequencyMenu,
                    onDismissRequest = { showFrequencyMenu = false },
                ) {
                    ScheduleFrequencyPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.displayName) },
                            onClick = {
                                viewModel.frequencyPreset.value = preset
                                showFrequencyMenu = false
                            },
                        )
                    }
                }
            }

            // ── By-Meter fields ────────────────────────────────────────────────
            if (scheduleType == ScheduleTypeSelection.BY_METER) {
                // Meter type
                OutlinedTextField(
                    value = meterType,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMeterTypeMenu = true },
                    label = { Text(stringResource(R.string.schedule_field_meter_type)) },
                    enabled = false,
                    readOnly = true,
                )
                DropdownMenu(
                    expanded = showMeterTypeMenu,
                    onDismissRequest = { showMeterTypeMenu = false },
                ) {
                    METER_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                viewModel.meterType.value = type
                                showMeterTypeMenu = false
                            },
                        )
                    }
                }

                // Meter interval
                OutlinedTextField(
                    value = meterInterval,
                    onValueChange = { viewModel.meterInterval.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.schedule_field_meter_interval)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )

                // Current reading / due at
                OutlinedTextField(
                    value = meterCurrent,
                    onValueChange = { viewModel.meterCurrent.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.schedule_field_meter_due)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }

            // ── Timezone ──────────────────────────────────────────────────────
            if (scheduleType == ScheduleTypeSelection.BY_DATE) {
                OutlinedTextField(
                    value = timezone,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimezoneMenu = true },
                    label = { Text("Timezone") },
                    enabled = false,
                    readOnly = true,
                )
                DropdownMenu(
                    expanded = showTimezoneMenu,
                    onDismissRequest = { showTimezoneMenu = false },
                ) {
                    val commonZones = listOf(
                        "UTC", "America/New_York", "America/Chicago", "America/Denver",
                        "America/Los_Angeles", "America/Phoenix", "America/Anchorage",
                        "Pacific/Honolulu", "Europe/London", "Europe/Paris", "Europe/Berlin",
                        "Asia/Tokyo", "Asia/Shanghai", "Asia/Kolkata", "Australia/Sydney",
                    )
                    commonZones.forEach { zoneId ->
                        DropdownMenuItem(
                            text = { Text(zoneId) },
                            onClick = {
                                viewModel.timezone.value = zoneId
                                showTimezoneMenu = false
                            },
                        )
                    }
                }

                // ── End repeat ────────────────────────────────────────────────
                Text(
                    text = "End Repeat",
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    endTypes.forEachIndexed { index, (key, label) ->
                        SegmentedButton(
                            selected = endType == key,
                            onClick = { viewModel.endType.value = key },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = endTypes.size,
                            ),
                            label = { Text(label) },
                        )
                    }
                }

                if (endType == "date") {
                    val displayDate = endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "Select date"
                    OutlinedTextField(
                        value = displayDate,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val today = endDate ?: LocalDate.now()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> viewModel.endDate.value = LocalDate.of(y, m + 1, d) },
                                    today.year, today.monthValue - 1, today.dayOfMonth,
                                ).show()
                            },
                        label = { Text("End Date") },
                        enabled = false,
                        readOnly = true,
                    )
                }

                if (endType == "count") {
                    OutlinedTextField(
                        value = endCount.toString(),
                        onValueChange = { viewModel.endCount.value = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Number of Occurrences") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            // ── Notes ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Save button ────────────────────────────────────────────────────
            Button(
                onClick = viewModel::save,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isSaving) stringResource(R.string.schedule_saving)
                    else stringResource(R.string.schedule_save)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
