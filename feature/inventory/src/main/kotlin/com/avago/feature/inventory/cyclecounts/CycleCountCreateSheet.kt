package com.avago.feature.inventory.cyclecounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.CountScope
import com.avago.feature.inventory.R
import com.avago.feature.inventory.ui.LocationPickerSheet
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountCreateSheet(
    onDismiss: () -> Unit,
    onCreatedAndStarted: (String) -> Unit,
    viewModel: CreateCycleCountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdCountId) {
        state.createdCountId?.let(onCreatedAndStarted)
    }

    if (state.showLocationPicker) {
        LocationPickerSheet(
            onLocationPicked = viewModel::setLocation,
            onDismiss = { viewModel.showLocationPicker(false) },
            showClearOption = false,
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.scheduledOnMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setScheduledOn(dateState.selectedDateMillis)
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setScheduledOn(null)
                    showDatePicker = false
                }) { Text(stringResource(R.string.po_clear_date)) }
            },
        ) { DatePicker(state = dateState) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.cycle_count_create_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            ListItem(
                overlineContent = { Text(stringResource(R.string.cycle_count_location_label)) },
                headlineContent = { Text(state.locationName ?: stringResource(R.string.cycle_count_select_location)) },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.showLocationPicker(true) },
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.cycle_count_scope_label), style = MaterialTheme.typography.labelMedium)
            CREATABLE_COUNT_SCOPES.forEach { scope ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setScope(scope) },
                ) {
                    RadioButton(selected = state.scope == scope, onClick = { viewModel.setScope(scope) })
                    Text(countScopeLabel(scope))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cycle_count_blind), modifier = Modifier.weight(1f))
                Switch(checked = state.blindCount, onCheckedChange = viewModel::setBlindCount)
            }

            ListItem(
                overlineContent = { Text(stringResource(R.string.cycle_count_scheduled_on)) },
                headlineContent = {
                    Text(
                        state.scheduledOnMs?.let {
                            DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                                .format(Date(it))
                        } ?: "—",
                    )
                },
                leadingContent = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.cycle_count_notes)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cycle_count_start_now), modifier = Modifier.weight(1f))
                Switch(checked = state.startNow, onCheckedChange = viewModel::setStartNow)
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting && state.locationId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.isSubmitting -> stringResource(R.string.cycle_count_submitting)
                        state.startNow -> stringResource(R.string.cc_create_btn)
                        else -> stringResource(R.string.cycle_count_create_only)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun countScopeLabel(scope: CountScope): String = when (scope) {
    CountScope.FULL -> stringResource(R.string.cycle_count_scope_full)
    CountScope.CATEGORY -> stringResource(R.string.cycle_count_scope_category)
    CountScope.BIN -> stringResource(R.string.cycle_count_scope_zone)
    CountScope.PART_LIST -> scope.wire
    CountScope.ABC_A -> stringResource(R.string.cycle_count_scope_abc_a)
    CountScope.ABC_B -> stringResource(R.string.cycle_count_scope_abc_b)
    CountScope.ABC_C -> stringResource(R.string.cycle_count_scope_abc_c)
}
