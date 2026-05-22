package com.avago.feature.workorders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.WoTransition
import com.avago.feature.workorders.model.availableTransitions
import com.avago.feature.workorders.model.summariseRrule
import com.avago.feature.workorders.model.statusColor
import com.avago.feature.workorders.ui.components.AssigneeAvatar
import com.avago.feature.workorders.ui.components.WoStatusChip
import com.avago.feature.workorders.ui.components.WoTimerView
import com.avago.feature.workorders.ui.sheets.RescheduleSheet
import com.avago.feature.workorders.ui.sheets.RepeatsSheet
import com.avago.feature.workorders.ui.sheets.TechPickerSheet
import com.avago.feature.workorders.viewmodel.WorkOrderDetailViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailScreen(
    woId: String,
    onBack: () -> Unit,
    onEdit: (woId: String) -> Unit,
    onTechClick: (techId: String) -> Unit = {},
    onAddPart: () -> Unit = {},
    onManageCostLines: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val wo by viewModel.workOrder.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val checklistItems by viewModel.checklistItems.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTransitionMenu by remember { mutableStateOf(false) }
    var showTechPicker by remember { mutableStateOf(false) }
    var showRepeatsSheet by remember { mutableStateOf(false) }
    var showRescheduleSheet by remember { mutableStateOf(false) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var dispatcherNotesDraft by rememberSaveable { mutableStateOf("") }

    // Initialise the dispatcher notes draft the first time the WO loads.
    // Using a Boolean flag ensures we do this exactly once even if the WO
    // entity is updated (and dispatcherNotes changes) by a background sync
    // later — otherwise the effect would re-fire and clobber an in-progress
    // edit whenever the draft happened to be blank.
    var dispatcherNotesInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wo?.woId) {
        if (!dispatcherNotesInitialized && wo != null) {
            dispatcherNotesDraft = wo!!.dispatcherNotes ?: ""
            dispatcherNotesInitialized = true
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.wo_detail_confirm_delete)) },
            text = { Text(stringResource(R.string.wo_detail_confirm_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteWorkOrder(onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.wo_detail_delete_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.wo_detail_cancel_btn))
                }
            },
        )
    }

    if (showTechPicker) {
        TechPickerSheet(
            selectedTechIds = assignments.map { it.technicianId },
            onDismiss = { showTechPicker = false },
            onConfirm = { techIds ->
                // Assignments are written by ViewModel
                showTechPicker = false
            },
            woId = woId,
        )
    }

    if (showRescheduleSheet) {
        val currentDueDate = wo?.dueDate?.let { ms ->
            java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }
        RescheduleSheet(
            currentDueDate = currentDueDate,
            onDismiss = { showRescheduleSheet = false },
            onConfirm = { newDate ->
                viewModel.reschedule(newDate)
                showRescheduleSheet = false
            },
        )
    }

    if (showRepeatsSheet) {
        RepeatsSheet(
            currentRrule = wo?.rrule,
            currentEndType = wo?.endType,
            currentEndCount = wo?.endCount?.toInt(),
            currentEndDateMs = wo?.endDate,
            onDismiss = { showRepeatsSheet = false },
            onSave = { rrule ->
                viewModel.saveRecurrence(rrule)
                showRepeatsSheet = false
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wo_detail_back),
                        )
                    }
                },
                title = { Text(wo?.title ?: stringResource(R.string.wo_detail_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.wo_detail_overflow),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wo_detail_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onEdit(woId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reschedule") },
                                onClick = {
                                    showOverflowMenu = false
                                    showRescheduleSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.wo_detail_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (wo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val currentWo = wo!!
        val status = WoStatus.fromKey(currentWo.status)
        val transitions = status.availableTransitions()

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ── Status header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WoStatusChip(status = status)
                if (transitions.isNotEmpty()) {
                    Box {
                        Button(onClick = { showTransitionMenu = true }) {
                            Text(stringResource(R.string.wo_detail_transition_btn))
                        }
                        DropdownMenu(
                            expanded = showTransitionMenu,
                            onDismissRequest = { showTransitionMenu = false },
                        ) {
                            transitions.forEach { transition ->
                                DropdownMenuItem(
                                    text = { Text(transition.label) },
                                    onClick = {
                                        showTransitionMenu = false
                                        viewModel.transitionStatus(transition.targetStatus)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Info section ──
            DetailSection(title = "Details") {
                LabeledRow("Asset", currentWo.assetId ?: stringResource(R.string.wo_detail_no_asset))
                LabeledRow("Priority", currentWo.priority?.replaceFirstChar { it.uppercase() } ?: "—")
                LabeledRow("Due", currentWo.dueDate?.let { formatDate(it) } ?: "No due date")
                if (currentWo.status == "in_progress" && currentWo.timerStartedAt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    WoTimerView(startedAtMs = currentWo.timerStartedAt!!)
                }
                currentWo.estimatedEffortMinutes?.let { mins ->
                    LabeledRow("Est. Hours", String.format("%.1f h", mins / 60.0))
                }
                if (currentWo.woKind == "recurring_parent") {
                    LabeledRow("Type", "Recurring")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Recurrence card ──
            Card(
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
                            stringResource(R.string.wo_detail_section_recurrence),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        TextButton(onClick = { showRepeatsSheet = true }) {
                            Text(stringResource(R.string.wo_detail_edit_recurrence))
                        }
                    }
                    Text(
                        text = summariseRrule(currentWo.rrule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Assignments ──
            DetailSection(
                title = stringResource(R.string.wo_detail_section_assignments),
                action = {
                    IconButton(
                        onClick = { showTechPicker = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.wo_detail_add_assignee))
                    }
                },
            ) {
                if (assignments.isEmpty()) {
                    Text(
                        text = "No technicians assigned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        assignments.forEach { assignment ->
                            AssigneeAvatar(
                                initials = assignment.technicianId.take(2).uppercase(),
                                modifier = Modifier.clickable {
                                    onTechClick(assignment.technicianId)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Checklist ──
            DetailSection(title = stringResource(R.string.wo_detail_section_checklist)) {
                if (checklistItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wo_detail_checklist_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    checklistItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.isCompleted,
                                onCheckedChange = { viewModel.toggleChecklistItem(item) },
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Parts used ──
            DetailSection(
                title = "Parts",
                action = {
                    IconButton(
                        onClick = onAddPart,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Part")
                    }
                },
            ) {
                Text(
                    text = "Tap + to issue parts from inventory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Costs ──
            DetailSection(
                title = stringResource(R.string.wo_detail_section_costs),
                action = {
                    TextButton(onClick = onManageCostLines) {
                        Text("Manage")
                    }
                },
            ) {
                currentWo.laborCost?.let { LabeledRow(stringResource(R.string.wo_detail_labor_cost), formatCurrency(it, currentWo.currency)) }
                currentWo.partsCost?.let { LabeledRow(stringResource(R.string.wo_detail_parts_cost), formatCurrency(it, currentWo.currency)) }
                currentWo.totalCost?.let { LabeledRow(stringResource(R.string.wo_detail_total_cost), formatCurrency(it, currentWo.currency)) }
                if (currentWo.laborCost == null && currentWo.partsCost == null && currentWo.totalCost == null) {
                    Text(
                        text = "No cost data. Tap Manage to add cost lines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ── Dispatcher notes ──
            DetailSection(title = stringResource(R.string.wo_detail_section_dispatcher)) {
                OutlinedTextField(
                    value = dispatcherNotesDraft,
                    onValueChange = { dispatcherNotesDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.wo_detail_dispatcher_placeholder)) },
                    minLines = 2,
                    maxLines = 5,
                    // Auto-save on focus lost would require a FocusRequester — handled on change with a
                    // debounce in a production build; for now, save on every change.
                )
                if (dispatcherNotesDraft != (currentWo.dispatcherNotes ?: "")) {
                    TextButton(
                        onClick = { viewModel.saveDispatcherNotes(dispatcherNotesDraft) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Save Notes")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Comments ──
            DetailSection(title = stringResource(R.string.wo_detail_section_comments)) {
                // Composer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.wo_detail_comment_placeholder)) },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            viewModel.addComment(commentText)
                            commentText = ""
                        },
                        enabled = commentText.isNotBlank(),
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = stringResource(R.string.wo_detail_comment_send),
                        )
                    }
                }

                if (comments.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.wo_detail_comments_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    comments.take(20).forEach { comment ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = comment.authorId.take(8),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = comment.body,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun DetailSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable Column.() -> Unit,
) {
    Card(
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
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Column.LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatDate(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun formatCurrency(amount: Double, currencyCode: String?): String {
    return try {
        val currency = Currency.getInstance(currencyCode ?: "USD")
        val fmt = NumberFormat.getCurrencyInstance(Locale.US)
        fmt.currency = currency
        fmt.format(amount)
    } catch (_: Exception) {
        String.format("%.2f", amount)
    }
}
