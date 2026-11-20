package com.avago.feature.docs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.avago.core.data.domain.DocumentKind
import com.avago.feature.docs.R
import com.avago.feature.docs.model.DocTypes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Material date pickers work in UTC midnight millis. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocDatePicker(initial: LocalDate?, onPicked: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val dpState = rememberDatePickerState(initialSelectedDateMillis = initial?.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(dpState.selectedDateMillis?.fromPickerMillis()) }) {
                Text(stringResource(R.string.doc_add_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_add_cancel)) }
        },
    ) { DatePicker(state = dpState) }
}

@Composable
internal fun DateField(label: String, value: LocalDate?, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    OutlinedTextField(
        value = value?.format(dateFormatter).orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.doc_add_pick_date)) },
        trailingIcon = {
            Row {
                if (value != null && onClear != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.doc_clear_date))
                    }
                }
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.doc_add_pick_date))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** The document kind picker shared by the add and edit forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KindField(kind: DocumentKind, onKind: (DocumentKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedType = DocTypes.forKind(kind)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = stringResource(selectedType.labelResId),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.doc_add_field_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DocTypes.all.forEach { typeItem ->
                DropdownMenuItem(
                    text = { Text(stringResource(typeItem.labelResId)) },
                    leadingIcon = { Icon(typeItem.icon, contentDescription = null) },
                    onClick = { onKind(typeItem.kind); expanded = false },
                )
            }
        }
    }
}
