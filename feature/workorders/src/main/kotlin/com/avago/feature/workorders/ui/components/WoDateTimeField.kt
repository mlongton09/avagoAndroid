package com.avago.feature.workorders.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.feature.workorders.R
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Labelled date + time buttons for an epoch-millis instant, picked as wall-clock time in [zone]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WoDateTimeField(
    label: String,
    valueMillis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val local = Instant.ofEpochMilli(valueMillis).atZone(zone)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDate = true }) {
                Text(local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
            OutlinedButton(onClick = { showTime = true }) {
                Text(local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
            }
        }
    }
    if (showDate) {
        // DatePicker works in UTC millis of the selected date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(ZonedDateTime.of(d, local.toLocalTime(), zone).toInstant().toEpochMilli())
                    }
                    showDate = false
                }) { Text(stringResource(R.string.wo_save)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.wo_cancel)) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute)
                    onChange(ZonedDateTime.of(local.toLocalDate(), t, zone).toInstant().toEpochMilli())
                    showTime = false
                }) { Text(stringResource(R.string.wo_save)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.wo_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}
