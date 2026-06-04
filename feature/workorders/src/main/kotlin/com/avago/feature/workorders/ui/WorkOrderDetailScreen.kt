package com.avago.feature.workorders.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.AutoAwesome
import com.avago.core.ai.ui.ScoutPaletteSheet
import com.avago.core.ui.CategoryBadge
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoPriority
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.WoTransition
import com.avago.feature.workorders.model.availableTransitions
import com.avago.feature.workorders.model.summariseRrule
import com.avago.feature.workorders.ui.components.AssigneeAvatar
import com.avago.feature.workorders.ui.components.priorityBarColor
import com.avago.feature.workorders.ui.sheets.RepeatsSheet
import com.avago.feature.workorders.ui.sheets.RescheduleSheet
import com.avago.feature.workorders.ui.sheets.TechPickerSheet
import com.avago.feature.workorders.viewmodel.AuditEntry
import com.avago.feature.workorders.viewmodel.WorkOrderDetailViewModel
import com.avago.feature.workorders.viewmodel.WorkOrderMapPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@androidx.compose.material3.ExperimentalMaterial3Api
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
    /** Called when the WO status qualifies for the merged log screen — replaces this screen in the stack. */
    onNavigateToLogScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val wo by viewModel.workOrder.collectAsStateWithLifecycle()
    val assetName by viewModel.assetName.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val checklistItems by viewModel.checklistItems.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val auditHistory by viewModel.auditHistory.collectAsStateWithLifecycle()
    val chatThreadId by viewModel.chatThreadId.collectAsStateWithLifecycle()
    val isEditingHeader by viewModel.isEditingHeader.collectAsStateWithLifecycle()
    val editTitle by viewModel.editTitle.collectAsStateWithLifecycle()
    val editDescription by viewModel.editDescription.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val canApprove by viewModel.canApprove.collectAsStateWithLifecycle()
    val canDelete by viewModel.canDelete.collectAsStateWithLifecycle()
    val mapPreview by viewModel.mapPreview.collectAsStateWithLifecycle()

    var showScoutSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTechPicker by remember { mutableStateOf(false) }
    var showRepeatsSheet by remember { mutableStateOf(false) }
    var showRescheduleSheet by remember { mutableStateOf(false) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var dispatcherNotesDraft by rememberSaveable { mutableStateOf("") }
    var dispatcherNotesInitialized by rememberSaveable { mutableStateOf(false) }

    val shouldShowLogView by viewModel.shouldShowLogView.collectAsStateWithLifecycle()
    // Auto-route to the merged log screen when the WO status qualifies —
    // mirrors iOS routeToLogItemViewIfNeeded() which replaces the detail VC.
    LaunchedEffect(shouldShowLogView) {
        if (shouldShowLogView) onNavigateToLogScreen?.invoke()
    }

    LaunchedEffect(wo?.woId) {
        if (!dispatcherNotesInitialized && wo != null) {
            dispatcherNotesDraft = wo?.dispatcherNotes ?: ""
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
            onConfirm = {
                showTechPicker = false
            },
            woId = woId,
        )
    }

    if (showRescheduleSheet) {
        val currentDueDate = wo?.dueDate?.let { ms ->
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
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

    ScoutPaletteSheet(
        visible = showScoutSheet,
        onDismiss = { showScoutSheet = false },
        onNavigate = { targetScreen, fields ->
            when (targetScreen) {
                "log_entry" -> onLogWork?.invoke(wo?.assetId)
                else -> Unit
            }
            showScoutSheet = false
        },
    )

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = { showScoutSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Scout")
                }
                if (onLogWork != null) {
                    FloatingActionButton(
                        onClick = { onLogWork(wo?.assetId) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Log work")
                    }
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
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
                            if (canEdit) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.wo_detail_edit)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onEdit(woId)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wo_detail_reschedule)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showRescheduleSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wo_export_pdf)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportPdf(context)
                                },
                            )
                            if (canDelete) {
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

        val currentWo = wo ?: return@Scaffold
        val transitionStatus = transitionStatusFor(currentWo.status)
        val transitions = transitionStatus.availableTransitions()
        val primaryAssignee = assignments.firstOrNull()?.technicianId
            ?: currentWo.assignedTo
            ?: stringResource(R.string.wo_detail_no_technicians)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
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
                if (canApprove) {
                    ApprovalBanner(
                        approvalState = currentWo.approvalState,
                        onApprove = viewModel::approveWorkOrder,
                        onReject = viewModel::rejectWorkOrder,
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(16.dp))

                    HeaderCard(
                        title = currentWo.title,
                        description = currentWo.description,
                        priority = currentWo.priority,
                        category = currentWo.category,
                        statusKey = currentWo.status,
                        isEditing = isEditingHeader,
                        editTitle = editTitle,
                        editDescription = editDescription,
                        canEdit = canEdit,
                        onEditTitleChanged = viewModel::onEditTitleChanged,
                        onEditDescriptionChanged = viewModel::onEditDescriptionChanged,
                        onStartEditing = viewModel::startEditingHeader,
                        onSave = viewModel::saveHeader,
                        onCancel = viewModel::cancelEditingHeader,
                    )

                    if (transitions.isNotEmpty() && canEdit) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TransitionsCard(
                            transitions = transitions,
                            onTransition = { viewModel.transitionStatus(it.targetStatus) },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    CollapsibleDetailSection(
                        title = stringResource(R.string.wo_detail_section_details),
                        initiallyExpanded = true,
                    ) {
                        LabeledRow(
                            label = stringResource(R.string.wo_detail_asset_label),
                            value = assetName ?: currentWo.assetId ?: stringResource(R.string.wo_detail_no_asset),
                        )
                        currentWo.estimatedEffortMinutes?.let { minutes ->
                            LabeledRow(
                                label = stringResource(R.string.wo_detail_est_hours_label),
                                value = stringResource(R.string.wo_detail_minutes_format, minutes.toString()),
                            )
                        }
                        LabeledRow(
                            label = stringResource(R.string.wo_detail_due_label),
                            value = currentWo.dueDate?.let(::formatDate) ?: stringResource(R.string.wo_detail_no_due_date),
                            onClick = { showRescheduleSheet = true },
                            enabled = canEdit,
                        )
                        LabeledRow(
                            label = stringResource(R.string.wo_field_repeats),
                            value = summariseRrule(currentWo.rrule),
                            onClick = { showRepeatsSheet = true },
                            enabled = canEdit,
                        )
                        LabeledRow(
                            label = stringResource(R.string.wo_detail_created_label),
                            value = formatDate(currentWo.createdAt),
                        )
                        if (!currentWo.logId.isNullOrBlank()) {
                            LabeledRow(
                                label = stringResource(R.string.wo_detail_log_entry_label),
                                value = stringResource(R.string.wo_detail_view),
                            )
                        }

                        mapPreview?.let { preview ->
                            Spacer(modifier = Modifier.height(12.dp))
                            WorkOrderMapCard(
                                preview = preview,
                                onOpenMaps = {
                                    val encodedAddress = Uri.encode(preview.address)
                                    val uri = if (preview.latitude != null && preview.longitude != null) {
                                        Uri.parse("geo:${preview.latitude},${preview.longitude}?q=${preview.latitude},${preview.longitude}($encodedAddress)")
                                    } else {
                                        Uri.parse("geo:0,0?q=$encodedAddress")
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    CollapsibleDetailSection(
                        title = stringResource(R.string.wo_detail_section_assignments),
                        initiallyExpanded = true,
                    ) {
                        LabeledRow(
                            label = stringResource(R.string.wo_detail_assigned_to_label),
                            value = primaryAssignee,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canEdit) {
                                TextButton(onClick = { showTechPicker = true }) {
                                    Text(
                                        if (assignments.isEmpty()) {
                                            stringResource(R.string.wo_detail_add_assignee)
                                        } else {
                                            stringResource(R.string.wo_detail_reassign)
                                        },
                                    )
                                }
                                if (assignments.isNotEmpty()) {
                                    TextButton(onClick = { showTechPicker = true }) {
                                        Text(stringResource(R.string.wo_field_add_assignee))
                                    }
                                }
                            }
                            if (assignments.isEmpty()) {
                                TextButton(onClick = { viewModel.claimWorkOrder() }) {
                                    Text(stringResource(R.string.wo_detail_claim))
                                }
                            }
                        }
                    }

                    if (canEdit || !currentWo.dispatcherNotes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CollapsibleDetailSection(
                            title = stringResource(R.string.wo_detail_section_dispatcher),
                            initiallyExpanded = true,
                        ) {
                            OutlinedTextField(
                                value = dispatcherNotesDraft,
                                onValueChange = { if (canEdit) dispatcherNotesDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.wo_detail_dispatcher_placeholder)) },
                                minLines = 2,
                                maxLines = 5,
                                readOnly = !canEdit,
                            )
                            if (canEdit && dispatcherNotesDraft != (currentWo.dispatcherNotes ?: "")) {
                                TextButton(
                                    onClick = { viewModel.saveDispatcherNotes(dispatcherNotesDraft) },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(stringResource(R.string.wo_detail_save_notes))
                                }
                            }
                        }
                    }

                    if (checklistItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CollapsibleDetailSection(
                            title = stringResource(R.string.wo_detail_section_checklist),
                            initiallyExpanded = true,
                        ) {
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

                    CollapsibleDetailSection(
                        title = stringResource(R.string.wo_detail_section_history),
                        initiallyExpanded = false,
                    ) {
                        HistoryContent(auditHistory = auditHistory)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    CollapsibleDetailSection(
                        title = stringResource(R.string.wo_detail_section_comments),
                        initiallyExpanded = true,
                    ) {
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
                                enabled = commentText.isNotBlank() && !isSaving,
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

                    if (chatThreadId != null && onOpenChat != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TeamChatCard(threadId = chatThreadId!!, onOpenChat = onOpenChat)
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkOrderMapCard(
    preview: WorkOrderMapPreview,
    onOpenMaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = preview.address,
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Map preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        preview.latitude?.let { lat ->
                            preview.longitude?.let { lon ->
                                Text(
                                    text = "$lat, $lon",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    TextButton(onClick = onOpenMaps) {
                        Text("Open in Maps")
                    }
                }
            }
        }
    }
}

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
                stringResource(R.string.status_awaiting_approval),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(R.string.wo_approval_required_message),
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

@Composable
private fun HeaderCard(
    title: String,
    description: String?,
    priority: String?,
    category: String?,
    statusKey: String,
    isEditing: Boolean,
    editTitle: String,
    editDescription: String,
    canEdit: Boolean,
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
                    label = { Text(stringResource(R.string.wo_field_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = onEditDescriptionChanged,
                    label = { Text(stringResource(R.string.wo_field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (canEdit) {
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

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityPill(priority = priority)
                CategoryBadge(categoryId = category)
                StatusBadge(statusKey = statusKey)
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.wo_detail_cancel_btn)) }
                    Button(onClick = onSave) { Text(stringResource(R.string.wo_save)) }
                }
            }
        }
    }
}

@Composable
private fun PriorityPill(priority: String?) {
    val label = WoPriority.fromKey(priority).displayName
    val color = priorityBarColor(priority)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StatusBadge(statusKey: String) {
    val color = woStatusColor(statusKey)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = workOrderStatusLabel(statusKey),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TransitionsCard(
    transitions: List<WoTransition>,
    onTransition: (WoTransition) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.wo_detail_section_transitions),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(8.dp))
            transitions.forEachIndexed { index, transition ->
                val buttonColor = if (transition.targetStatus == WoStatus.CANCELLED) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color(0xFF16A34A)
                }
                TextButton(
                    onClick = { onTransition(transition) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = buttonColor),
                ) {
                    Text(
                        text = transition.label,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (index < transitions.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CollapsibleDetailSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun HistoryContent(auditHistory: List<AuditEntry>) {
    if (auditHistory.isEmpty()) {
        Text(
            text = stringResource(R.string.wo_detail_history_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
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

@Composable
private fun LabeledRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null && enabled) {
                    base.clickable(onClick = onClick)
                } else {
                    base
                }
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick != null && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TeamChatCard(
    threadId: String,
    onOpenChat: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChat(threadId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.wo_detail_open_team_chat),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.rotate(90f),
            )
        }
    }
}

private fun formatDate(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun transitionStatusFor(status: String): WoStatus = when (status.lowercase()) {
    "draft" -> WoStatus.DRAFT
    "pending_review", "reviewed", "open" -> WoStatus.OPEN
    "assigned" -> WoStatus.ASSIGNED
    "in_progress" -> WoStatus.IN_PROGRESS
    "on_hold" -> WoStatus.ON_HOLD
    "completed", "complete" -> WoStatus.COMPLETE
    "cancelled" -> WoStatus.CANCELLED
    else -> WoStatus.OPEN
}

private fun workOrderStatusLabel(status: String): String = when (status.lowercase()) {
    "pending_review" -> "Pending Review"
    "in_progress" -> "In Progress"
    "on_hold" -> "On Hold"
    "completed", "complete" -> "Completed"
    else -> status.replace('_', ' ').replaceFirstChar { it.titlecase() }
}

private fun woStatusColor(status: String): Color = when (status.lowercase()) {
    "draft" -> Color(0xFF9CA3AF)
    "pending_review" -> Color(0xFFFBBF24)
    "reviewed" -> Color(0xFF14B8A6)
    "assigned", "open" -> Color(0xFF3B82F6)
    "in_progress", "on_hold" -> Color(0xFFF97316)
    "completed", "complete" -> Color(0xFF22C55E)
    "cancelled" -> Color(0xFFEF4444)
    else -> Color(0xFF9CA3AF)
}
