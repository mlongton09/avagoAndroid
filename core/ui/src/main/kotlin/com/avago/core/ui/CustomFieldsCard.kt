package com.avago.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldEntry
import com.avago.core.data.domain.CustomFieldType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The account's custom fields for one record with their values; members allowed to edit the
 * record can change them. Nothing is shown when the account has no fields for [entity].
 */
@Composable
fun CustomFieldsCard(
    entity: CustomFieldEntity,
    entityId: String,
    modifier: Modifier = Modifier,
    viewModel: CustomFieldsViewModel = hiltViewModel(key = "custom_fields:${entity.wire}:$entityId"),
) {
    LaunchedEffect(entity, entityId) { viewModel.bind(entity, entityId) }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    if (entries.isEmpty()) return
    var editing by remember { mutableStateOf<CustomFieldEntry?>(null) }

    editing?.let { entry ->
        CustomFieldValueDialog(
            entry = entry,
            onSet = { value ->
                viewModel.set(entry.def, value)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.custom_fields_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            entries.forEach { entry ->
                CustomFieldRow(entry, canEdit, onEdit = { editing = entry }, onToggle = { viewModel.set(entry.def, it.toString()) })
            }
            error?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).clickable { viewModel.errorShown() },
                )
            }
        }
    }
}

@Composable
private fun CustomFieldRow(entry: CustomFieldEntry, canEdit: Boolean, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val def = entry.def
    val label = if (def.required) stringResource(R.string.custom_fields_required_label, def.name) else def.name
    if (def.type == CustomFieldType.CHECKBOX) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = entry.value == "true", onCheckedChange = onToggle, enabled = canEdit)
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = canEdit, onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            entry.value?.let { displayValue(def.type, it) } ?: stringResource(R.string.custom_fields_unset),
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun displayValue(type: CustomFieldType, value: String): String = when (type) {
    CustomFieldType.DATE -> runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(value)
    else -> value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldValueDialog(entry: CustomFieldEntry, onSet: (String?) -> Unit, onDismiss: () -> Unit) {
    val def = entry.def
    if (def.type == CustomFieldType.DATE) {
        val initial = entry.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    state.selectedDateMillis?.let { onSet(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                }) { Text(stringResource(R.string.custom_fields_save)) }
            },
            dismissButton = {
                Row {
                    if (entry.value != null && !def.required) {
                        TextButton(onClick = { onSet(null) }) { Text(stringResource(R.string.custom_fields_clear)) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.custom_fields_cancel)) }
                }
            },
        ) { DatePicker(state = state) }
        return
    }

    var text by rememberSaveable { mutableStateOf(entry.value.orEmpty()) }
    val valid = isValid(def, text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(def.name) },
        text = {
            if (def.type == CustomFieldType.SELECT) {
                SelectInput(def, text, onSelect = { text = it })
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = def.type == CustomFieldType.NUMBER,
                    isError = !valid,
                    keyboardOptions = if (def.type == CustomFieldType.NUMBER) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSet(text.trim().ifEmpty { null }) }) { Text(stringResource(R.string.custom_fields_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.custom_fields_cancel)) } },
    )
}

/** Blank clears the value, which a required field does not allow. */
private fun isValid(def: CustomFieldDef, text: String): Boolean {
    val v = text.trim()
    if (v.isEmpty()) return !def.required
    return when (def.type) {
        CustomFieldType.NUMBER -> v.toBigDecimalOrNull() != null
        CustomFieldType.SELECT -> v in def.options
        else -> true
    }
}

@Composable
private fun SelectInput(def: CustomFieldDef, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val unset = stringResource(R.string.custom_fields_unset)
    Box {
        OutlinedTextField(
            value = selected.ifEmpty { unset },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!def.required) {
                DropdownMenuItem(text = { Text(unset) }, onClick = {
                    open = false
                    onSelect("")
                })
            }
            def.options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}
