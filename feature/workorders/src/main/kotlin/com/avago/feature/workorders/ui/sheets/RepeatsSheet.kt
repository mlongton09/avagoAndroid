package com.avago.feature.workorders.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.core.data.domain.RecurrenceRule
import com.avago.core.data.domain.WorkOrderRecurrence
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.RecurrenceFrequency
import com.avago.feature.workorders.model.buildRrule
import com.avago.feature.workorders.model.rruleInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val END_NEVER = "never"
private const val END_COUNT = "count"
private const val END_DATE = "date"

/**
 * Edits a work order's calendar recurrence (`maint.work_orders.set_recurrence`).
 * [onStop] is offered only while an active recurrence exists; it saves an empty rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatsSheet(
    current: WorkOrderRecurrence?,
    onDismiss: () -> Unit,
    onSave: (RecurrenceRule) -> Unit,
    onStop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = current?.takeIf { it.active && it.rrule.isNotBlank() }

    var frequency by remember { mutableStateOf(RecurrenceFrequency.fromRrule(active?.rrule)) }
    var interval by remember { mutableStateOf(rruleInterval(active?.rrule).toString()) }
    var endType by remember { mutableStateOf(active?.endType?.takeIf { it == END_COUNT || it == END_DATE } ?: END_NEVER) }
    var endCount by remember { mutableStateOf((active?.endCount ?: 10).toString()) }
    var endDate by remember {
        mutableStateOf(active?.endOnEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusMonths(3))
    }
    var pickDate by remember { mutableStateOf(false) }

    val intervalValue = interval.toIntOrNull()?.takeIf { it >= 1 }
    val countValue = endCount.toIntOrNull()?.takeIf { it >= 1 }
    val valid = (frequency != RecurrenceFrequency.CUSTOM || intervalValue != null) && (endType != END_COUNT || countValue != null)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.repeats_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.repeats_frequency))
            RecurrenceFrequency.entries.forEach { f ->
                RadioRow(label = frequencyLabel(f), selected = frequency == f, onClick = { frequency = f })
            }
            if (frequency == RecurrenceFrequency.CUSTOM) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { v -> interval = v.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.repeats_interval_weeks)) },
                    isError = intervalValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionLabel(stringResource(R.string.repeats_ends))
            RadioRow(stringResource(R.string.repeats_end_never), endType == END_NEVER) { endType = END_NEVER }
            RadioRow(stringResource(R.string.repeats_end_after), endType == END_COUNT) { endType = END_COUNT }
            if (endType == END_COUNT) {
                OutlinedTextField(
                    value = endCount,
                    onValueChange = { v -> endCount = v.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.repeats_occurrences)) },
                    isError = countValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                )
            }
            RadioRow(stringResource(R.string.repeats_end_on_date), endType == END_DATE) { endType = END_DATE }
            if (endType == END_DATE) {
                TextButton(onClick = { pickDate = true }, modifier = Modifier.padding(start = 24.dp)) {
                    Text(endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (active != null) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.repeats_stop))
                    }
                } else {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.repeats_cancel))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        onSave(
                            RecurrenceRule(
                                rrule = buildRrule(frequency, intervalValue ?: 1),
                                endType = endType,
                                endCount = countValue.takeIf { endType == END_COUNT },
                                endOnEpochDay = endDate.toEpochDay().takeIf { endType == END_DATE },
                                timezone = active?.timezone ?: ZoneId.systemDefault().id,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.repeats_save))
                }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { endDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        pickDate = false
                    },
                ) { Text(stringResource(R.string.repeats_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.repeats_cancel)) }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

@Composable
private fun frequencyLabel(f: RecurrenceFrequency): String = stringResource(
    when (f) {
        RecurrenceFrequency.DAILY -> R.string.repeats_frequency_daily
        RecurrenceFrequency.WEEKLY -> R.string.repeats_frequency_weekly
        RecurrenceFrequency.BIWEEKLY -> R.string.repeat_biweekly
        RecurrenceFrequency.MONTHLY -> R.string.repeats_frequency_monthly
        RecurrenceFrequency.QUARTERLY -> R.string.repeat_quarterly
        RecurrenceFrequency.SEMIANNUAL -> R.string.repeat_semiannual
        RecurrenceFrequency.YEARLY -> R.string.repeats_frequency_yearly
        RecurrenceFrequency.CUSTOM -> R.string.repeats_frequency_custom
    },
)

/** One-line summary of [recurrence] for the details row. */
@Composable
fun recurrenceSummary(recurrence: WorkOrderRecurrence?): String {
    val r = recurrence?.takeIf { it.active && it.rrule.isNotBlank() } ?: return stringResource(R.string.repeats_none)
    val f = RecurrenceFrequency.fromRrule(r.rrule)
    val freq = if (f == RecurrenceFrequency.CUSTOM) {
        stringResource(R.string.repeats_every_n_weeks, rruleInterval(r.rrule))
    } else {
        frequencyLabel(f)
    }
    val endCount = r.endCount
    val endOn = r.endOnEpochDay
    val end = when {
        r.endType == END_COUNT && endCount != null -> stringResource(R.string.repeats_ends_after, endCount)
        r.endType == END_DATE && endOn != null -> stringResource(
            R.string.repeats_ends_on,
            LocalDate.ofEpochDay(endOn).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        )
        else -> stringResource(R.string.repeats_never_ends)
    }
    return "$freq \u00b7 $end"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
