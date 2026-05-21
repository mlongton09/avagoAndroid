package com.avago.feature.log.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avago.feature.log.model.InspectionFieldDef

/**
 * Renders a list of inspection form fields based on their [InspectionFieldDef] type.
 * All state changes are reported via [onAnswerChanged].
 *
 * Supported field types:
 * - "checkbox"  → Checkbox row
 * - "select"    → Exposed dropdown
 * - "text"      → OutlinedTextField (multiline)
 * - "number"    → OutlinedTextField (numeric keyboard)
 * - "pass_fail" → Two-button row (Pass / Fail)
 */
@Composable
fun InspectionFormRenderer(
    fields: List<InspectionFieldDef>,
    answers: Map<String, String>,
    onAnswerChanged: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { field ->
            val current = answers[field.key] ?: ""
            when (field.type) {
                "checkbox" -> CheckboxField(
                    label = field.label,
                    checked = current == "true",
                    onCheckedChange = { checked -> onAnswerChanged(field.key, if (checked) "true" else "false") },
                )

                "select" -> SelectField(
                    label = field.label,
                    options = field.options,
                    selected = current,
                    onSelected = { onAnswerChanged(field.key, it) },
                )

                "number" -> OutlinedTextField(
                    value = current,
                    onValueChange = { onAnswerChanged(field.key, it) },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    singleLine = true,
                )

                "pass_fail" -> PassFailField(
                    label = field.label,
                    value = current,
                    onValueChanged = { onAnswerChanged(field.key, it) },
                )

                else -> /* "text" and unknown */ OutlinedTextField(
                    value = current,
                    onValueChange = { onAnswerChanged(field.key, it) },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Field composables
// ---------------------------------------------------------------------------

@Composable
private fun CheckboxField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PassFailField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onValueChanged("pass") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (value == "pass") Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (value == "pass") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Pass")
            }
            Button(
                onClick = { onValueChanged("fail") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (value == "fail") Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (value == "fail") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Fail")
            }
        }
    }
}
