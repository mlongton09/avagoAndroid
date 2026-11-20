package com.avago.feature.workorders.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.feature.workorders.R
import com.avago.feature.workorders.ui.components.WoDateTimeField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleSheet(
    currentDueDate: LocalDate?,
    currentPlannedStart: Long?,
    currentPlannedEnd: Long?,
    onDismiss: () -> Unit,
    onConfirm: (newDate: LocalDate, plannedWindow: Pair<Long, Long>?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val initialMs = currentDueDate
        ?.atStartOfDay(ZoneId.of("UTC"))
        ?.toInstant()
        ?.toEpochMilli()

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
    var hasWindow by remember { mutableStateOf(currentPlannedStart != null && currentPlannedEnd != null) }
    val defaultStart = remember {
        java.time.ZonedDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
    }
    var plannedStart by remember { mutableLongStateOf(currentPlannedStart ?: defaultStart) }
    var plannedEnd by remember { mutableLongStateOf(currentPlannedEnd ?: (plannedStart + 2 * 3_600_000L)) }
    val windowValid = !hasWindow || plannedEnd > plannedStart

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.reschedule_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 4.dp),
            )

            if (currentDueDate != null) {
                Text(
                    text = stringResource(R.string.reschedule_current_due, currentDueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reschedule_planned_window),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = hasWindow, onCheckedChange = { hasWindow = it })
            }
            if (hasWindow) {
                WoDateTimeField(
                    label = stringResource(R.string.reschedule_planned_start),
                    valueMillis = plannedStart,
                    onChange = { plannedStart = it },
                    modifier = Modifier.padding(top = 4.dp),
                )
                WoDateTimeField(
                    label = stringResource(R.string.reschedule_planned_end),
                    valueMillis = plannedEnd,
                    onChange = { plannedEnd = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!windowValid) {
                    Text(
                        text = stringResource(R.string.reschedule_window_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reschedule_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val selectedMs = datePickerState.selectedDateMillis
                        if (selectedMs != null) {
                            val newDate = Instant.ofEpochMilli(selectedMs)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onConfirm(newDate, if (hasWindow) plannedStart to plannedEnd else null)
                            }
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null && windowValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reschedule_confirm))
                }
            }
        }
    }
}
