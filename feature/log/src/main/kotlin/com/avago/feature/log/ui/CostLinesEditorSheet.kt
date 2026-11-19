package com.avago.feature.log.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.feature.log.model.LogCostLineDraft
import com.avago.feature.log.model.LogPersonOption
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.avago.feature.log.R
import com.avago.feature.log.model.LogPacks
import java.util.UUID
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider

/**
 * ModalBottomSheet for managing a list of cost line drafts.
 *
 * Each row lets the user set:
 *  - kind (part / labor) via chip toggle
 *  - description / name
 *  - quantity and unit cost
 *  - optional tax amount
 *
 * Parts may reference a catalog product (added by scanning its barcode); labor lines pick the
 * technician from [technicians].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostLinesEditorSheet(
    costLines: List<LogCostLineDraft>,
    onAdd: (LogCostLineDraft) -> Unit,
    onUpdate: (LogCostLineDraft) -> Unit,
    onRemove: (lineId: String) -> Unit,
    onDismiss: () -> Unit,
    technicians: List<LogPersonOption> = emptyList(),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.log_cost_lines_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            // List of existing draft lines
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(costLines, key = { it.lineId }) { draft ->
                    CostLineDraftRow(
                        draft = draft,
                        technicians = technicians,
                        onUpdate = onUpdate,
                        onRemove = { onRemove(draft.lineId) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Add buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        onAdd(
                            LogCostLineDraft(
                                lineId = UUID.randomUUID().toString(),
                                kind = "part",
                                displayOrder = costLines.size,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.log_cost_lines_add_part)) }

                Button(
                    onClick = {
                        onAdd(
                            LogCostLineDraft(
                                lineId = UUID.randomUUID().toString(),
                                kind = "labor",
                                displayOrder = costLines.size,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.log_cost_lines_add_labor)) }
            }

            Spacer(Modifier.height(8.dp))

            // Done
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.log_action_done)) }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CostLineDraftRow(
    draft: LogCostLineDraft,
    technicians: List<LogPersonOption>,
    onUpdate: (LogCostLineDraft) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header row: kind chips + delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = draft.kind == "part",
                onClick = { onUpdate(draft.copy(kind = "part", performerUserId = null)) },
                label = { Text(stringResource(R.string.log_cost_line_part)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = draft.kind == "labor",
                onClick = { onUpdate(draft.copy(kind = "labor", productId = null, productName = null)) },
                label = { Text(stringResource(R.string.log_cost_line_labor)) },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.log_cost_lines_remove))
            }
        }

        Spacer(Modifier.height(6.dp))

        if (draft.kind == "part" && draft.productId != null) {
            Text(
                text = draft.productName ?: draft.productId,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
        }

        if (draft.kind == "labor") {
            TechnicianField(
                selectedUserId = draft.performerUserId,
                technicians = technicians,
                onSelect = { onUpdate(draft.copy(performerUserId = it)) },
            )
            Spacer(Modifier.height(6.dp))
        }

        // Description
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onUpdate(draft.copy(description = it)) },
            label = { Text(stringResource(R.string.log_cost_lines_description)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))

        // Qty + unit cost
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DecimalField(
                key = draft.lineId + ":qty",
                value = draft.quantity,
                onValue = { it?.let { q -> onUpdate(draft.copy(quantity = q)) } },
                label = stringResource(R.string.log_cost_lines_qty),
                modifier = Modifier.weight(1f),
            )
            DecimalField(
                key = draft.lineId + ":unit",
                value = draft.unitCost.takeIf { it != 0.0 },
                onValue = { onUpdate(draft.copy(unitCost = it ?: 0.0)) },
                label = stringResource(R.string.log_cost_lines_unit_cost),
                modifier = Modifier.weight(1f),
            )
        }

        // Advanced toggle
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(stringResource(if (showAdvanced) R.string.log_cost_lines_hide_advanced else R.string.log_cost_lines_show_advanced))
        }

        if (showAdvanced) {
            DecimalField(
                key = draft.lineId + ":tax",
                value = draft.taxAmount,
                onValue = { onUpdate(draft.copy(taxAmount = it)) },
                label = stringResource(R.string.log_cost_lines_tax),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechnicianField(
    selectedUserId: String?,
    technicians: List<LogPersonOption>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = technicians.firstOrNull { it.userId == selectedUserId }?.name ?: selectedUserId.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.log_cost_lines_technician)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_cost_lines_no_technician)) },
                onClick = { onSelect(null); expanded = false },
            )
            technicians.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.name) },
                    onClick = { onSelect(person.userId); expanded = false },
                )
            }
        }
    }
}

/**
 * Decimal input that keeps the user's raw text (so "1." / "12,5" can be typed) and only
 * reports parsed values upward. Accepts a comma as decimal separator (German keyboards).
 * Re-syncs from [value] only when it changes externally.
 */
@Composable
private fun DecimalField(
    key: String,
    value: Double?,
    onValue: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var text by remember(key) { mutableStateOf(value?.let { LogPacks.formatNumber(it) } ?: "") }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            if (s.isEmpty() || s.matches(Regex("^\\d*([.,]\\d*)?$"))) {
                text = s
                onValue(s.trim().replace(',', '.').toDoubleOrNull())
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = text.isNotBlank() && parsed == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}
