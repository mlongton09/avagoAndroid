package com.avago.feature.rentals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avago.feature.rentals.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Material 3 date + time picker field. Times are picked as wall-clock time in [zone] (the
 * rental location's ZoneId, spec §8) and converted to an [Instant].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(
    label: String,
    value: Instant,
    zone: ZoneId,
    onChange: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val local = value.atZone(zone)
    val dateText = formatDate(value, zone)
    val timeText = formatTime(value, zone)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { showDate = true },
            modifier = Modifier
                .heightIn(min = MinTouch)
                .semantics { contentDescription = "$label, $dateText" },
        ) { Text("$label: $dateText") }
        OutlinedButton(
            onClick = { showTime = true },
            modifier = Modifier
                .heightIn(min = MinTouch)
                .semantics { contentDescription = "$label, $timeText" },
        ) { Text(timeText) }
    }
    if (showDate) {
        // DatePicker works in UTC millis of the selected *date*.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(combine(d, local.toLocalTime(), zone))
                    }
                    showDate = false
                }) { Text(stringResource(R.string.rentals_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.rentals_cancel)) } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(combine(local.toLocalDate(), LocalTime.of(state.hour, state.minute), zone))
                    showTime = false
                }) { Text(stringResource(R.string.rentals_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.rentals_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

/** Wall-clock date + time in [zone] → instant (DST gaps resolve forward, like java.time). */
internal fun combine(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
    ZonedDateTime.of(date, time, zone).toInstant()

/** Default booking window: next full hour → +1 day, in [zone]. */
internal fun defaultWindow(zone: ZoneId, now: Instant = Instant.now()): Pair<Instant, Instant> {
    val start = now.atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
    return start.toInstant() to start.plusDays(1).toInstant()
}
