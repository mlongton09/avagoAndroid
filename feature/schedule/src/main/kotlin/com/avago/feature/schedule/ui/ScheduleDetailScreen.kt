package com.avago.feature.schedule.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.PmOccurrence
import com.avago.core.data.domain.PmOccurrenceState
import com.avago.core.data.domain.PmTrigger
import com.avago.core.data.domain.PmTriggerKind
import com.avago.core.data.domain.WorkOrder
import com.avago.feature.schedule.R
import com.avago.feature.schedule.ui.components.PmStatusBadge
import com.avago.feature.schedule.ui.components.pmDueText
import com.avago.feature.schedule.util.PmSchedule
import com.avago.feature.schedule.util.RruleHelper
import com.avago.feature.schedule.util.addOccurrenceToAndroidCalendar
import com.avago.feature.schedule.viewmodel.ScheduleAction
import com.avago.feature.schedule.viewmodel.ScheduleDetailUiState
import com.avago.feature.schedule.viewmodel.ScheduleDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    onBack: () -> Unit,
    onLogService: (assetId: String) -> Unit,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val eventText = event?.let {
        stringResource(
            when (it) {
                ScheduleAction.COMPLETED -> R.string.schedule_completed_message
                ScheduleAction.SKIPPED -> R.string.schedule_skipped_message
                ScheduleAction.DEFERRED -> R.string.schedule_deferred_message
                ScheduleAction.FAILED -> R.string.schedule_action_failed
            },
        )
    }
    LaunchedEffect(event) {
        if (eventText != null) {
            viewModel.onEventShown()
            snackbarHostState.showSnackbar(eventText)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.schedule_detail_back))
                    }
                },
                title = { Text(stringResource(R.string.schedule_detail_title)) },
            )
        },
    ) { innerPadding ->
        val occurrence = state.occurrence
        when {
            occurrence != null -> ScheduleDetailContent(
                state = state,
                occurrence = occurrence,
                isSaving = isSaving,
                onComplete = viewModel::complete,
                onSkip = viewModel::skip,
                onDefer = viewModel::defer,
                onLogService = onLogService,
                onOpenWorkOrder = onOpenWorkOrder,
                modifier = Modifier.padding(innerPadding),
            )
            state.isLoading -> Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.schedule_detail_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class PendingDialog { COMPLETE, SKIP, DEFER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDetailContent(
    state: ScheduleDetailUiState,
    occurrence: PmOccurrence,
    isSaving: Boolean,
    onComplete: (java.math.BigDecimal?) -> Unit,
    onSkip: (String) -> Unit,
    onDefer: (Long) -> Unit,
    onLogService: (String) -> Unit,
    onOpenWorkOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val plan = state.plan
    val now = System.currentTimeMillis()
    val isOpen = occurrence.state == PmOccurrenceState.OPEN
    var dialog by rememberSaveable { mutableStateOf<PendingDialog?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(occurrence.planTitle, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                occurrence.assetName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PmStatusBadge(PmSchedule.status(occurrence, now))
        }

        HorizontalDivider()

        DetailSection(title = stringResource(R.string.schedule_detail_section_info)) {
            DetailRow(stringResource(R.string.schedule_detail_next_due), pmDueText(occurrence) ?: stringResource(R.string.schedule_detail_not_set))
            DetailRow(stringResource(R.string.schedule_detail_cycle), occurrence.cycleNo.toString())
            plan?.triggers?.forEach { trigger ->
                DetailRow(stringResource(R.string.schedule_detail_frequency), triggerText(trigger))
            }
            DetailRow(
                stringResource(R.string.schedule_detail_last_completed),
                plan?.lastCompletedAt?.let(::formatDate) ?: stringResource(R.string.schedule_detail_never),
            )
            occurrence.closedAt?.let { DetailRow(stringResource(R.string.schedule_detail_closed), formatDate(it)) }
            plan?.description?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.schedule_detail_notes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.workOrder?.let { wo ->
            DetailSection(title = stringResource(R.string.schedule_detail_work_order)) {
                WorkOrderRow(wo, onClick = { onOpenWorkOrder(wo.id) })
            }
        }

        DetailSection(title = stringResource(R.string.schedule_detail_history_title)) {
            if (state.history.isEmpty()) {
                Text(
                    stringResource(R.string.schedule_detail_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.history.forEach { HistoryRow(it, now) }
            }
        }

        if (isOpen) {
            Button(onClick = { dialog = PendingDialog.COMPLETE }, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.schedule_action_complete))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { dialog = PendingDialog.SKIP }, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_action_skip))
                }
                OutlinedButton(onClick = { dialog = PendingDialog.DEFER }, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_action_defer))
                }
            }
        }
        occurrence.assetId?.let { assetId ->
            OutlinedButton(onClick = { onLogService(assetId) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_complete_service))
            }
        }
        if (isOpen && occurrence.dueAt != null) {
            OutlinedButton(onClick = { addOccurrenceToAndroidCalendar(context, occurrence, plan) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.schedule_detail_add_to_calendar))
            }
        }
        Spacer(Modifier.height(48.dp))
    }

    when (dialog) {
        PendingDialog.COMPLETE -> CompleteDialog(
            askMeter = occurrence.dueValue != null || plan?.triggers?.any { it.kind == PmTriggerKind.METER } == true,
            onDismiss = { dialog = null },
            onConfirm = { value -> dialog = null; onComplete(value) },
        )
        PendingDialog.SKIP -> SkipDialog(onDismiss = { dialog = null }, onConfirm = { reason -> dialog = null; onSkip(reason) })
        PendingDialog.DEFER -> DeferDialog(
            initial = occurrence.dueAt,
            onDismiss = { dialog = null },
            onConfirm = { dueAt -> dialog = null; onDefer(dueAt) },
        )
        null -> Unit
    }
}

@Composable
private fun triggerText(t: PmTrigger): String = when (t.kind) {
    PmTriggerKind.CALENDAR -> RruleHelper.describe(t.rrule)
    PmTriggerKind.INTERVAL -> stringResource(R.string.schedule_trigger_interval, t.intervalValue ?: 0, t.intervalUnit.orEmpty())
    PmTriggerKind.METER -> stringResource(R.string.schedule_trigger_meter, t.meterInterval?.let(PmSchedule::formatValue) ?: "?")
}

private fun formatDate(epochMs: Long): String =
    PmSchedule.localDate(epochMs).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun WorkOrderRow(wo: WorkOrder, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(wo.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            wo.status.wire.replace('_', ' ').replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(o: PmOccurrence, now: Long) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.schedule_history_cycle, o.cycleNo), style = MaterialTheme.typography.bodyMedium)
            (o.closedAt ?: o.dueAt)?.let {
                Text(formatDate(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PmStatusBadge(PmSchedule.status(o, now))
    }
}

@Composable
private fun CompleteDialog(askMeter: Boolean, onDismiss: () -> Unit, onConfirm: (java.math.BigDecimal?) -> Unit) {
    var meter by rememberSaveable { mutableStateOf("") }
    val parsed = PmSchedule.parseValue(meter)
    val invalid = meter.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_complete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.schedule_complete_body))
                if (askMeter) {
                    OutlinedTextField(
                        value = meter,
                        onValueChange = { meter = it },
                        label = { Text(stringResource(R.string.schedule_complete_meter)) },
                        isError = invalid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed) }, enabled = !invalid) { Text(stringResource(R.string.schedule_action_complete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_detail_cancel_btn)) } },
    )
}

@Composable
private fun SkipDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_skip_title)) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.schedule_skip_reason)) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text(stringResource(R.string.schedule_action_skip)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_detail_cancel_btn)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeferDialog(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    // The picker works in UTC midnights; the new due time is that day's start in the local zone.
    val today = java.time.LocalDate.now()
    val start = initial?.let(PmSchedule::localDate)?.takeIf { it.isAfter(today) } ?: today.plusDays(1)
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.schedule_action_defer)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_detail_cancel_btn)) } },
    ) {
        DatePicker(state = pickerState, title = { Text(stringResource(R.string.schedule_defer_title), modifier = Modifier.padding(16.dp)) })
    }
}
