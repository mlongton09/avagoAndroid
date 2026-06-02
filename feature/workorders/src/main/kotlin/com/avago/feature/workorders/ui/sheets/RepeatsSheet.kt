package com.avago.feature.workorders.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.RecurrenceEndType
import com.avago.feature.workorders.model.RecurrenceFrequency
import com.avago.feature.workorders.model.buildRrule
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatsSheet(
    currentRrule: String?,
    currentEndType: String?,
    currentEndCount: Int?,
    currentEndDateMs: Long?,
    onDismiss: () -> Unit,
    onSave: (rrule: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Parse existing state
    val initialFreq = RecurrenceFrequency.fromRrule(currentRrule)
    val initialEndType = RecurrenceEndType.fromKey(currentEndType)
    val initialInterval = currentRrule
        ?.split(";")
        ?.firstOrNull { it.startsWith("INTERVAL=") }
        ?.removePrefix("INTERVAL=")
        ?.toIntOrNull() ?: 1

    var frequency by remember { mutableStateOf(initialFreq) }
    var interval by remember { mutableIntStateOf(initialInterval) }
    var endType by remember { mutableStateOf(initialEndType) }
    var endCount by remember { mutableIntStateOf(currentEndCount ?: 10) }
    var endDate by remember {
        mutableStateOf(
            currentEndDateMs?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: LocalDate.now().plusMonths(1)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.repeats_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Frequency picker ──
            Text(
                text = "Frequency",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                RecurrenceFrequency.DAILY,
                RecurrenceFrequency.WEEKLY,
                RecurrenceFrequency.BIWEEKLY,
                RecurrenceFrequency.MONTHLY,
                RecurrenceFrequency.QUARTERLY,
                RecurrenceFrequency.SEMIANNUAL,
                RecurrenceFrequency.YEARLY,
                RecurrenceFrequency.CUSTOM,
            ).forEach { freq ->
                RadioRow(
                    label = when (freq) {
                        RecurrenceFrequency.DAILY -> stringResource(R.string.repeats_frequency_daily)
                        RecurrenceFrequency.WEEKLY -> stringResource(R.string.repeats_frequency_weekly)
                        RecurrenceFrequency.BIWEEKLY -> stringResource(R.string.repeat_biweekly)
                        RecurrenceFrequency.MONTHLY -> stringResource(R.string.repeats_frequency_monthly)
                        RecurrenceFrequency.QUARTERLY -> stringResource(R.string.repeat_quarterly)
                        RecurrenceFrequency.SEMIANNUAL -> stringResource(R.string.repeat_semiannual)
                        RecurrenceFrequency.YEARLY -> stringResource(R.string.repeats_frequency_yearly)
                        RecurrenceFrequency.CUSTOM -> stringResource(R.string.repeats_frequency_custom)
                    },
                    selected = frequency == freq,
                    onClick = { frequency = freq },
                )
            }

            // Interval field (for CUSTOM)
            if (frequency == RecurrenceFrequency.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = interval.toString(),
                    onValueChange = { interval = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                    label = { Text(stringResource(R.string.repeats_interval)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ── End type ──
            Text(
                text = "Ends",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            RadioRow(
                label = stringResource(R.string.repeats_end_never),
                selected = endType == RecurrenceEndType.NEVER,
                onClick = { endType = RecurrenceEndType.NEVER },
            )

            RadioRow(
                label = stringResource(R.string.repeats_end_after),
                selected = endType == RecurrenceEndType.AFTER_COUNT,
                onClick = { endType = RecurrenceEndType.AFTER_COUNT },
            )
            if (endType == RecurrenceEndType.AFTER_COUNT) {
                OutlinedTextField(
                    value = endCount.toString(),
                    onValueChange = { endCount = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                    label = { Text(stringResource(R.string.repeats_occurrences)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                )
            }

            RadioRow(
                label = stringResource(R.string.repeats_end_on_date),
                selected = endType == RecurrenceEndType.ON_DATE,
                onClick = { endType = RecurrenceEndType.ON_DATE },
            )
            if (endType == RecurrenceEndType.ON_DATE) {
                Text(
                    text = endDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 32.dp, top = 4.dp)
                        .clickable {
                            // In a production build, open a DatePickerDialog here.
                            // Advancing by one month as a placeholder action.
                            endDate = endDate.plusMonths(1)
                        },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.repeats_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val zone = ZoneId.systemDefault()
                        val untilStr = if (endType == RecurrenceEndType.ON_DATE) {
                            endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'000000'Z'"))
                        } else null

                        val rrule = buildRrule(
                            frequency = frequency,
                            interval = if (frequency == RecurrenceFrequency.CUSTOM) interval else 1,
                            endType = endType,
                            count = if (endType == RecurrenceEndType.AFTER_COUNT) endCount else null,
                            until = untilStr,
                        )
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onSave(rrule)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.repeats_save))
                }
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
