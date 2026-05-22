package com.avago.feature.log.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
 * Color map for well-known inspection option values.
 * Used to render colored chips on "select" and "pass_fail" fields.
 */
val INSPECTION_OPTION_COLORS = mapOf(
    "normal"       to Color(0xFF4CAF50), // green
    "monitor"      to Color(0xFFFF9800), // orange
    "needs_repair" to Color(0xFFF44336), // red
    "na"           to Color(0xFF9E9E9E), // gray
    "pass"         to Color(0xFF4CAF50), // green
    "fail"         to Color(0xFFF44336), // red
)

/**
 * Renders a list of inspection form fields based on their [InspectionFieldDef] type.
 * All state changes are reported via [onAnswerChanged].
 *
 * Supported field types:
 * - "checkbox"    → Checkbox row
 * - "select"      → Color-coded FilterChip row
 * - "text"        → OutlinedTextField (multiline)
 * - "number"      → OutlinedTextField (numeric keyboard); shows unit suffix when available
 * - "measurement" → Same as "number" but unit is required; always shows unit suffix
 * - "pass_fail"   → Color-coded Pass / Fail chip row
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

                "select" -> ColorChipSelectField(
                    label = field.label,
                    options = field.options,
                    selected = current,
                    onSelected = { onAnswerChanged(field.key, it) },
                )

                "number" -> NumberField(
                    label = field.label,
                    value = current,
                    unit = null,
                    onValueChange = { onAnswerChanged(field.key, it) },
                )

                "measurement" -> NumberField(
                    label = field.label,
                    value = current,
                    unit = field.options.firstOrNull(), // convention: unit stored as first option
                    onValueChange = { onAnswerChanged(field.key, it) },
                )

                "pass_fail" -> PassFailChipField(
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

@Composable
private fun NumberField(
    label: String,
    value: String,
    unit: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        ),
        singleLine = true,
        suffix = if (unit != null) ({ Text(unit) }) else null,
    )
}

/**
 * Renders select-type inspection options as color-coded [FilterChip]s.
 * The selected chip shows the option's semantic color as its container;
 * unselected chips use the surface color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChipSelectField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val optionColor = INSPECTION_OPTION_COLORS[option.lowercase()]
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = optionColor ?: MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

/**
 * Renders pass/fail as color-coded [FilterChip]s — green for pass, red for fail.
 */
@Composable
private fun PassFailChipField(
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
            FilterChip(
                selected = value == "pass",
                onClick = { onValueChanged("pass") },
                label = { Text("Pass") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = INSPECTION_OPTION_COLORS["pass"] ?: Color(0xFF4CAF50),
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            FilterChip(
                selected = value == "fail",
                onClick = { onValueChanged("fail") },
                label = { Text("Fail") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = INSPECTION_OPTION_COLORS["fail"] ?: Color(0xFFF44336),
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
