package com.avago.feature.workorders.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.avago.feature.workorders.viewmodel.AuditEntry
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
    onOpenChat: ((threadId: String) -> Unit)? = null,
    onLogWork: ((assetId: String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val wo by viewModel.workOrder.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val checklistItems by viewModel.checklistItems.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val auditHistory by viewModel.auditHistory.collectAsStateWithLifecycle()
    val chatThreadId by viewModel.chatThreadId.collectAsStateWithLifecycle()
    val isEditingHeader by viewModel.isEditingHeader.collectAsStateWithLifecycle()
    val editTitle by viewModel.editTitle.collectAsStateWithLifecycle()
    val editDescription by viewModel.editDescription.collectAsStateWithLifecycle()

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
            val safeWo = wo ?: return@LaunchedEffect
            dispatcherNotesDraft = safeWo.dispatcherNotes ?: ""
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
        floatingActionButton = {
            if (onLogWork != null) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { onLogWork(wo?.assetId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Log work")
                }
            }
        },
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
                    // Chat button
                    IconButton(
                        onClick = {
                            chatThreadId?.let { onOpenChat?.invoke(it) }
                        },
                        enabled = chatThreadId != null && onOpenChat != null,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Open in Chat",
                            tint = if (chatThreadId != null && onOpenChat != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
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
                                text = { Text(stringResource(R.string.wo_detail_reschedule)) },
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

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                // ── Approval banner ──
                ApprovalBanner(
                    approvalState = currentWo.approvalState,
                    onApprove = { viewModel.approveWorkOrder() },
                    onReject = { viewModel.rejectWorkOrder() },
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Title / description header card ──
                    HeaderCard(
                        title = currentWo.title,
                        description = currentWo.description,
                        isEditing = isEditingHeader,
                        editTitle = editTitle,
                        editDescription = editDescription,
                        onEditTitleChanged = { viewModel.onEditTitleChanged(it) },
                        onEditDescriptionChanged = { viewModel.onEditDescriptionChanged(it) },
                        onStartEditing = { viewModel.startEditingHeader() },
                        onSave = { viewModel.saveHeader() },
                        onCancel = { viewModel.cancelEditingHeader() },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                    DetailSection(title = stringResource(R.string.wo_detail_section_details)) {
                        LabeledRow(stringResource(R.string.wo_detail_asset_label), currentWo.assetId ?: stringResource(R.string.wo_detail_no_asset))
                        LabeledRow(stringResource(R.string.wo_detail_priority_label), currentWo.priority?.replaceFirstChar { it.uppercase() } ?: "—")
                        LabeledRow(stringResource(R.string.wo_detail_due_label), currentWo.dueDate?.let { formatDate(it) } ?: stringResource(R.string.wo_detail_no_due_date))
                        currentWo.timerStartedAt?.takeIf { currentWo.status == "in_progress" }?.let { startedAt ->
                            Spacer(modifier = Modifier.height(4.dp))
                            WoTimerView(startedAtMs = startedAt)
                        }
                        currentWo.estimatedEffortMinutes?.let { mins ->
                            LabeledRow(stringResource(R.string.wo_detail_est_hours_label), String.format("%.1f h", mins / 60.0))
                        }
                        if (currentWo.woKind == "recurring_parent") {
                            LabeledRow(stringResource(R.string.wo_detail_type_label), stringResource(R.string.wo_detail_recurring_label))
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
                                text = stringResource(R.string.wo_detail_no_technicians),
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
                        title = stringResource(R.string.wo_detail_section_parts),
                        action = {
                            IconButton(
                                onClick = onAddPart,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.wo_detail_add_part))
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.wo_detail_parts_empty),
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
                                Text(stringResource(R.string.wo_detail_costs_manage))
                            }
                        },
                    ) {
                        currentWo.laborCost?.let { LabeledRow(stringResource(R.string.wo_detail_labor_cost), formatCurrency(it, currentWo.currency)) }
                        currentWo.partsCost?.let { LabeledRow(stringResource(R.string.wo_detail_parts_cost), formatCurrency(it, currentWo.currency)) }
                        currentWo.totalCost?.let { LabeledRow(stringResource(R.string.wo_detail_total_cost), formatCurrency(it, currentWo.currency)) }
                        if (currentWo.laborCost == null && currentWo.partsCost == null && currentWo.totalCost == null) {
                            Text(
                                text = stringResource(R.string.wo_detail_costs_empty),
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
                                Text(stringResource(R.string.wo_detail_save_notes))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Activity (audit history) ──
                    ActivityCard(auditHistory = auditHistory)
                    if (auditHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

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
                                    Icons.AutoMirrored.Filled.Send,
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
    }
}

// ---------------------------------------------------------------------------
// Approval banner
// ---------------------------------------------------------------------------

@Composable
private fun ApprovalBanner(
    approvalState: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    if (approvalState != "pending" && approvalState != "required") return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Awaiting Cost Approval",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "This work order requires cost approval before it can proceed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReject) { Text("Reject") }
                Button(onClick = onApprove) { Text("Approve") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header card (title + description with inline editing)
// ---------------------------------------------------------------------------

@Composable
private fun HeaderCard(
    title: String,
    description: String?,
    isEditing: Boolean,
    editTitle: String,
    editDescription: String,
    onEditTitleChanged: (String) -> Unit,
    onEditDescriptionChanged: (String) -> Unit,
    onStartEditing: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = onEditTitleChanged,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = onEditDescriptionChanged,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onSave) { Text("Save") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onStartEditing,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit title and description",
                        )
                    }
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Activity card (audit history)
// ---------------------------------------------------------------------------

@Composable
private fun ActivityCard(auditHistory: List<AuditEntry>) {
    if (auditHistory.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            auditHistory.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "• ${entry.description}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = entry.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
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
    content: @Composable ColumnScope.() -> Unit,
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
private fun ColumnScope.LabeledRow(label: String, value: String) {
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
