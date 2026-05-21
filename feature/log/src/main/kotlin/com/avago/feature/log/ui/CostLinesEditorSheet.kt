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
 *  - optional tax amount and GL code
 *
 * Parts can optionally have an inventory reference (inventory picker is surfaced as
 * a text field for the inventory name, since a dedicated picker sheet is a separate
 * feature outside this compose tree).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostLinesEditorSheet(
    costLines: List<LogCostLineDraft>,
    onAdd: (LogCostLineDraft) -> Unit,
    onUpdate: (LogCostLineDraft) -> Unit,
    onRemove: (lineId: String) -> Unit,
    onDismiss: () -> Unit,
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
                text = "Parts & Labor",
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
                ) { Text("Add Part") }

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
                ) { Text("Add Labor") }
            }

            Spacer(Modifier.height(8.dp))

            // Done
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CostLineDraftRow(
    draft: LogCostLineDraft,
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
                onClick = { onUpdate(draft.copy(kind = "part", userId = null, userName = null)) },
                label = { Text("Part") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = draft.kind == "labor",
                onClick = { onUpdate(draft.copy(kind = "labor", inventoryId = null, inventoryName = null)) },
                label = { Text("Labor") },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove line")
            }
        }

        Spacer(Modifier.height(6.dp))

        // Part: show inventory name field (free text for now)
        if (draft.kind == "part") {
            OutlinedTextField(
                value = draft.inventoryName ?: "",
                onValueChange = { onUpdate(draft.copy(inventoryName = it.ifBlank { null })) },
                label = { Text("Part name / inventory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
        }

        // Labor: show user name field
        if (draft.kind == "labor") {
            OutlinedTextField(
                value = draft.userName ?: "",
                onValueChange = { onUpdate(draft.copy(userName = it.ifBlank { null })) },
                label = { Text("Technician name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
        }

        // Description
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onUpdate(draft.copy(description = it)) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))

        // Qty + unit cost
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = if (draft.quantity == draft.quantity.toLong().toDouble())
                    draft.quantity.toLong().toString()
                else
                    draft.quantity.toString(),
                onValueChange = { str ->
                    str.toDoubleOrNull()?.let { onUpdate(draft.copy(quantity = it)) }
                },
                label = { Text("Qty") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = if (draft.unitCost == 0.0) "" else draft.unitCost.toString(),
                onValueChange = { str ->
                    onUpdate(draft.copy(unitCost = str.toDoubleOrNull() ?: 0.0))
                },
                label = { Text("Unit cost") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        // Advanced toggle
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced" else "Advanced (tax, GL)")
        }

        if (showAdvanced) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.taxAmount?.toString() ?: "",
                    onValueChange = { str ->
                        onUpdate(draft.copy(taxAmount = str.toDoubleOrNull()))
                    },
                    label = { Text("Tax") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = draft.glCode ?: "",
                    onValueChange = { onUpdate(draft.copy(glCode = it.ifBlank { null })) },
                    label = { Text("GL code") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
    }
}
