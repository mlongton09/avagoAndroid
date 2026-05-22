package com.avago.feature.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.ScheduleEntity
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.feature.schedule.R
import com.avago.feature.schedule.util.RruleHelper
import com.avago.feature.schedule.viewmodel.ScheduleDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    scheduleId: String,
    onBack: () -> Unit,
    onEdit: (scheduleId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val linkedWos by viewModel.linkedWos.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // Navigate back after deletion
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    // Show errors in snackbar
    LaunchedEffect(error) {
        error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.schedule_detail_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.schedule_detail_title)) },
                actions = {
                    IconButton(onClick = { onEdit(scheduleId) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.schedule_detail_edit),
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.schedule_detail_overflow),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.schedule_detail_delete)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                                onClick = {
                                    showOverflow = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (schedule == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val safeSchedule = schedule ?: error("unreachable: null branch handled above")
            ScheduleDetailContent(
                schedule = safeSchedule,
                isSaving = isSaving,
                linkedWos = linkedWos,
                onAddToCalendar = {
                    viewModel.addToCalendar(context, safeSchedule.title)
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // ── Delete confirmation dialog ──────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.schedule_detail_confirm_delete)) },
            text = { Text(stringResource(R.string.schedule_detail_confirm_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    },
                ) {
                    Text(
                        stringResource(R.string.schedule_detail_delete_confirm_btn),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.schedule_detail_cancel_btn))
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleDetailContent(
    schedule: ScheduleEntity,
    isSaving: Boolean,
    linkedWos: List<WorkOrderEntity>,
    onAddToCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOverdue = remember(schedule) { RruleHelper.isOverdue(schedule) }
    val isDueSoon = remember(schedule) { RruleHelper.isDueSoon(schedule) }

    val statusColor = when {
        !schedule.isActive -> Color.Gray
        isOverdue -> MaterialTheme.colorScheme.error
        isDueSoon -> Color(0xFFF59E0B)
        else -> Color(0xFF16A34A)
    }
    val statusLabel = when {
        !schedule.isActive -> stringResource(R.string.schedule_detail_status_inactive)
        isOverdue -> stringResource(R.string.schedule_detail_status_overdue)
        isDueSoon -> stringResource(R.string.schedule_detail_status_due_soon)
        else -> stringResource(R.string.schedule_detail_status_on_track)
    }

    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    fun Long?.fmtDate(): String = if (this == null) "—"
    else Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFmt)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                schedule.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Status badge
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        HorizontalDivider()

        // ── Frequency info card ────────────────────────────────────────────────
        DetailSection(title = stringResource(R.string.schedule_detail_section_info)) {
            DetailRow(
                label = stringResource(R.string.schedule_detail_frequency),
                value = RruleHelper.describe(schedule.rrule),
            )
            if (schedule.scheduleType == "meter") {
                DetailRow(
                    label = stringResource(R.string.schedule_detail_meter_type),
                    value = schedule.meterType ?: "—",
                )
                DetailRow(
                    label = stringResource(R.string.schedule_detail_meter_interval),
                    value = schedule.meterInterval?.let { "${it.toLong()} ${schedule.meterType ?: ""}".trim() } ?: "—",
                )
                DetailRow(
                    label = stringResource(R.string.schedule_detail_meter_due),
                    value = schedule.meterDue?.let { "${it.toLong()} ${schedule.meterType ?: ""}".trim() } ?: "—",
                )
            } else {
                DetailRow(
                    label = stringResource(R.string.schedule_detail_next_due),
                    value = schedule.nextDueAt.fmtDate(),
                )
                DetailRow(
                    label = stringResource(R.string.schedule_detail_last_completed),
                    value = schedule.lastCompletedAt.fmtDate(),
                )
            }
        }

        // ── History ────────────────────────────────────────────────────────────
        DetailSection(title = stringResource(R.string.schedule_detail_history_title)) {
            if (linkedWos.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_detail_history_no_wos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                linkedWos.forEach { wo ->
                    WoHistoryRow(wo = wo)
                }
            }
        }

        // ── Actions ────────────────────────────────────────────────────────────
        if (schedule.scheduleType != "meter") {
            OutlinedButton(
                onClick = onAddToCalendar,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.schedule_detail_add_to_calendar))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun WoHistoryRow(wo: WorkOrderEntity) {
    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val dateStr = Instant.ofEpochMilli(wo.updatedAt).atZone(ZoneId.systemDefault()).format(dateFmt)
    val statusColor = when (wo.status) {
        "complete" -> Color(0xFF16A34A)
        "cancelled" -> Color.Gray
        "in_progress" -> Color(0xFF2563EB)
        else -> Color(0xFFF59E0B)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(wo.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = statusColor.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = wo.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
