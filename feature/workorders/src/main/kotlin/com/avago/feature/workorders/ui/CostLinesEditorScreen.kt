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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.Formatters
import com.avago.core.data.domain.CostLine
import com.avago.core.data.domain.CostLineDraft
import com.avago.core.data.domain.CostLineType
import com.avago.core.data.domain.GlAccount
import com.avago.feature.workorders.R
import com.avago.feature.workorders.ui.sheets.TechPickerSheet
import com.avago.feature.workorders.viewmodel.CostLinesEditorViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostLinesEditorScreen(
    woId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CostLinesEditorViewModel = hiltViewModel(),
) {
    val costLines by viewModel.costLines.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val workerNames by viewModel.workerNames.collectAsStateWithLifecycle()
    val workerUserIds by viewModel.workerUserIds.collectAsStateWithLifecycle()
    val glAccounts by viewModel.glAccounts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<CostLine?>(null) }

    if (showAddDialog || editingLine != null) {
        CostLineDialog(
            initial = editingLine,
            initialPerformerUserId = editingLine?.performerWorkerId?.let(workerUserIds::get),
            workerNameByUserId = workerUserIds.entries.associate { (workerId, userId) -> userId to (workerNames[workerId] ?: userId) },
            glAccounts = glAccounts,
            onConfirm = { draft ->
                val existing = editingLine
                if (existing == null) viewModel.addLine(draft) else viewModel.updateLine(existing.id, draft)
                showAddDialog = false
                editingLine = null
            },
            onDismiss = {
                showAddDialog = false
                editingLine = null
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
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
                        text = Formatters.formatCurrency(total.toDouble(), null),
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
                items(costLines, key = { it.id }) { line ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.removeLine(line.id)
                                true
                            } else false
                        },
                    )
                    LaunchedEffect(line.id) {
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
                            performerName = line.performerWorkerId?.let(workerNames::get),
                            onClick = { editingLine = line },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun costLineTypeLabel(type: CostLineType): String = when (type) {
    CostLineType.LABOR -> stringResource(R.string.cost_lines_kind_labor)
    CostLineType.PART -> stringResource(R.string.cost_lines_kind_part)
    CostLineType.MISC -> stringResource(R.string.cost_lines_kind_misc)
}

@Composable
private fun CostLineCard(
    line: CostLine,
    performerName: String?,
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
                    text = line.description ?: costLineTypeLabel(line.kind),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.formatCurrency(line.total.toDouble(), null),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuggestionChip(onClick = {}, label = { Text(costLineTypeLabel(line.kind)) })
                Text(
                    text = "${line.quantity.stripTrailingZeros().toPlainString()} \u00d7 ${line.unitCost?.let { "%.2f".format(it) } ?: "\u2014"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (line.kind == CostLineType.LABOR && performerName != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${stringResource(R.string.cost_lines_field_technician)}: $performerName",
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
    initial: CostLine?,
    initialPerformerUserId: String?,
    workerNameByUserId: Map<String, String>,
    glAccounts: List<GlAccount>,
    onConfirm: (CostLineDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: CostLineType.LABOR) }
    var quantityText by rememberSaveable { mutableStateOf(initial?.quantity?.stripTrailingZeros()?.toPlainString() ?: "1") }
    var unitCostText by rememberSaveable { mutableStateOf(initial?.unitCost?.toPlainString() ?: "") }
    var performerUserId by rememberSaveable { mutableStateOf(initialPerformerUserId) }
    var kindMenuExpanded by remember { mutableStateOf(false) }
    var glAccountId by rememberSaveable { mutableStateOf(initial?.glAccountId) }
    var glMenuExpanded by remember { mutableStateOf(false) }
    var showTechPicker by remember { mutableStateOf(false) }
    val quantity = quantityText.trim().replace(',', '.').toBigDecimalOrNull()
    val unitCost = unitCostText.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
    val unitCostInvalid = unitCostText.isNotBlank() && unitCost == null

    if (showTechPicker) {
        TechPickerSheet(
            selectedTechIds = performerUserId?.let { listOf(it) } ?: emptyList(),
            onDismiss = { showTechPicker = false },
            onConfirm = { ids ->
                performerUserId = ids.firstOrNull()
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
                    expanded = kindMenuExpanded && initial == null,
                    onExpandedChange = { kindMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = costLineTypeLabel(kind),
                        onValueChange = {},
                        readOnly = true,
                        enabled = initial == null,
                        label = { Text(stringResource(R.string.cost_lines_field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = kindMenuExpanded && initial == null,
                        onDismissRequest = { kindMenuExpanded = false },
                    ) {
                        CostLineType.entries.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(costLineTypeLabel(k)) },
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
                        isError = quantity == null || quantity.signum() <= 0,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it },
                        label = { Text(stringResource(R.string.cost_lines_field_unit_cost)) },
                        isError = unitCostInvalid,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                if (glAccounts.isNotEmpty() || glAccountId != null) {
                    ExposedDropdownMenuBox(expanded = glMenuExpanded, onExpandedChange = { glMenuExpanded = it }) {
                        OutlinedTextField(
                            value = glAccountId?.let { id -> glAccounts.firstOrNull { it.id == id }?.label ?: id }
                                ?: stringResource(R.string.cost_lines_gl_none),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.cost_lines_field_gl_account)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(glMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = glMenuExpanded, onDismissRequest = { glMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cost_lines_gl_none)) },
                                onClick = {
                                    glAccountId = null
                                    glMenuExpanded = false
                                },
                            )
                            glAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.label) },
                                    onClick = {
                                        glAccountId = account.id
                                        glMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (kind == CostLineType.LABOR) {
                    OutlinedTextField(
                        value = performerUserId?.let { workerNameByUserId[it] ?: it }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.cost_lines_field_technician)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { showTechPicker = true }) {
                                Text(stringResource(R.string.cost_lines_pick_technician))
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = quantity != null && quantity.signum() > 0 && !unitCostInvalid,
                onClick = {
                    onConfirm(
                        // Fields this dialog does not edit keep their stored values.
                        CostLineDraft(
                            kind = kind,
                            description = description.trim().ifBlank { null },
                            productId = initial?.productId,
                            performerUserId = performerUserId.takeIf { kind == CostLineType.LABOR },
                            quantity = quantity ?: BigDecimal.ONE,
                            unitCost = unitCost,
                            taxAmountMinor = initial?.taxAmountMinor,
                            glAccountId = glAccountId,
                            estimate = initial?.isEstimate == true,
                            notes = initial?.notes,
                        ),
                    )
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
