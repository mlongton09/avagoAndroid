package com.avago.feature.workorders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.workorders.R
import com.avago.core.data.db.entity.LogCostLineEntity
import com.avago.feature.workorders.ui.sheets.TechPickerSheet
import com.avago.feature.workorders.viewmodel.CostLinesEditorViewModel

private val costLineKinds = listOf("Labor", "Material", "Subcontractor", "Equipment")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostLinesEditorScreen(
    woId: String,
    onBack: () -> Unit,
    onNavigateToGlPicker: () -> Unit,
    pendingGlAccount: String?,
    onGlAccountConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CostLinesEditorViewModel = hiltViewModel(),
) {
    val costLines by viewModel.costLines.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<LogCostLineEntity?>(null) }

    var dialogGlCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pendingGlAccount) {
        if (pendingGlAccount != null) {
            dialogGlCode = pendingGlAccount
            onGlAccountConsumed()
        }
    }

    if (showAddDialog || editingLine != null) {
        CostLineDialog(
            initial = editingLine,
            externalGlCode = dialogGlCode,
            onPickGlAccount = {
                onNavigateToGlPicker()
            },
            onConfirm = { description, kind, qty, unitCost, glCode, techId ->
                val existing = editingLine
                if (existing == null) {
                    viewModel.addLine(description, kind, qty, unitCost, glCode, techId)
                } else {
                    viewModel.updateLine(existing, description, kind, qty, unitCost, glCode, techId)
                }
                dialogGlCode = ""
                showAddDialog = false
                editingLine = null
            },
            onDismiss = {
                dialogGlCode = ""
                showAddDialog = false
                editingLine = null
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cost_lines_back))
                    }
                },
                title = { Text(stringResource(R.string.cost_lines_title)) },
                actions = {
                    Text(
                        text = formatCostAmount(total),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cost_lines_add_content_description))
            }
        },
    ) { innerPadding ->
        if (costLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.cost_lines_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(costLines, key = { it.lineId }) { line ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.removeLine(line.lineId)
                                true
                            } else false
                        },
                    )
                    LaunchedEffect(line.lineId) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            dismissState.reset()
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text(
                                    text = stringResource(R.string.cost_lines_delete),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                    ) {
                        CostLineCard(
                            line = line,
                            onClick = {
                                dialogGlCode = line.glCode ?: ""
                                editingLine = line
                            },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun CostLineCard(
    line: LogCostLineEntity,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line.description ?: line.kind,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCostAmount(line.quantity * line.unitCost),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuggestionChip(onClick = {}, label = { Text(line.kind) })
                Text(
                    text = "${line.quantity} × ${"%.2f".format(line.unitCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            line.glCode?.takeIf { it.isNotBlank() }?.let { glCode ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = glCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (line.kind == "Labor" && line.userId != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${stringResource(R.string.cost_lines_field_technician)}: ${line.userId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostLineDialog(
    initial: LogCostLineEntity?,
    externalGlCode: String,
    onPickGlAccount: () -> Unit,
    onConfirm: (description: String, kind: String, quantity: Double, unitCost: Double, glCode: String, techId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: costLineKinds.first()) }
    var quantityText by rememberSaveable { mutableStateOf(initial?.quantity?.toString() ?: "1") }
    var unitCostText by rememberSaveable { mutableStateOf(initial?.unitCost?.toString() ?: "0") }
    var glCode by rememberSaveable { mutableStateOf(initial?.glCode ?: "") }
    var techId by rememberSaveable { mutableStateOf(initial?.userId) }
    var kindMenuExpanded by remember { mutableStateOf(false) }
    var showTechPicker by remember { mutableStateOf(false) }

    LaunchedEffect(externalGlCode) {
        if (externalGlCode.isNotBlank()) {
            glCode = externalGlCode
        }
    }

    if (showTechPicker) {
        TechPickerSheet(
            selectedTechIds = techId?.let { listOf(it) } ?: emptyList(),
            onDismiss = { showTechPicker = false },
            onConfirm = { ids ->
                techId = ids.firstOrNull()
                showTechPicker = false
            },
            woId = null,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.cost_lines_dialog_add_title else R.string.cost_lines_dialog_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.cost_lines_field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = kindMenuExpanded,
                    onExpandedChange = { kindMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = kind,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.cost_lines_field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = kindMenuExpanded,
                        onDismissRequest = { kindMenuExpanded = false },
                    ) {
                        costLineKinds.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                onClick = {
                                    kind = k
                                    kindMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(stringResource(R.string.cost_lines_field_qty)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it },
                        label = { Text(stringResource(R.string.cost_lines_field_unit_cost)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = glCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.cost_lines_field_gl_account)) },
                    placeholder = { Text(stringResource(R.string.cost_lines_gl_account_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = onPickGlAccount) {
                            Text(stringResource(R.string.cost_lines_pick_gl_account))
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantityText.toDoubleOrNull() ?: 1.0
                    val cost = unitCostText.toDoubleOrNull() ?: 0.0
                    onConfirm(description, kind, qty, cost, glCode)
                },
            ) {
                Text(stringResource(R.string.cost_lines_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cost_lines_cancel)) }
        },
    )
}

private fun formatCostAmount(amount: Double): String = "${"%.2f".format(amount)}"
