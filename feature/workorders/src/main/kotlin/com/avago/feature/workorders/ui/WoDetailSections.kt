package com.avago.feature.workorders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avago.core.data.Formatters
import com.avago.core.data.domain.ApprovalDecision
import com.avago.core.data.domain.ApprovalRequest
import com.avago.core.data.domain.TimeEntry
import com.avago.core.data.domain.TimeEntryDraft
import com.avago.core.network.model.WorkOrderAuditEntry
import com.avago.core.network.model.WorkOrderBudget
import com.avago.feature.workorders.R
import com.avago.feature.workorders.ui.components.WoDateTimeField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatInstant(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

private fun formatIso(iso: String): String =
    runCatching { formatInstant(Instant.parse(iso).toEpochMilli()) }.getOrDefault(iso)

@Composable
private fun durationLabel(minutes: Long): String = stringResource(R.string.wo_time_duration, minutes / 60, minutes % 60)

/**
 * A pending cost approval ([request]) with its decisions so far. [canDecide] is false once the
 * signed-in user has decided or when the request is assigned to someone else.
 */
@Composable
internal fun CostApprovalCard(
    request: ApprovalRequest,
    canDecide: Boolean,
    enabled: Boolean,
    onDecide: (approve: Boolean, notes: String) -> Unit,
) {
    var notes by rememberSaveable(request.id) { mutableStateOf("") }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.wo_cost_approval_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            request.requestedAmountMinor?.let { minor ->
                Text(
                    stringResource(
                        R.string.wo_cost_approval_amount,
                        Formatters.formatCurrency(minor / 100.0, request.currency ?: "USD"),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            request.requestedAt?.let {
                Text(
                    stringResource(R.string.wo_cost_approval_requested_at, formatInstant(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            request.decisions.forEach { DecisionRow(it) }
            if (canDecide) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.wo_cost_approval_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = enabled, onClick = { onDecide(false, notes) }) { Text(stringResource(R.string.wo_reject)) }
                    Button(enabled = enabled, onClick = { onDecide(true, notes) }) { Text(stringResource(R.string.wo_approve)) }
                }
            }
        }
    }
}

@Composable
private fun DecisionRow(d: ApprovalDecision) {
    val verdict = stringResource(
        if (d.decision == "approved") R.string.wo_cost_approval_decision_approved else R.string.wo_cost_approval_decision_rejected,
    )
    Text(
        text = listOfNotNull(verdict, formatInstant(d.decidedAt), d.comment?.takeIf { it.isNotBlank() }).joinToString(" \u00b7 "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Time entries list; own editable entries can be edited/deleted, decidable ones approved/rejected by managers. */
@Composable
internal fun TimeEntriesContent(
    entries: List<TimeEntry>,
    myWorkerId: String?,
    canApprove: Boolean,
    enabled: Boolean,
    onLog: (TimeEntryDraft) -> Unit,
    onUpdate: (String, TimeEntryDraft) -> Unit,
    onDelete: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf<TimeEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf<TimeEntry?>(null) }
    val now = System.currentTimeMillis()

    if (entries.isEmpty()) {
        Text(
            stringResource(R.string.wo_time_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            stringResource(R.string.wo_time_total, durationLabel(entries.sumOf { it.minutes(now) })),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    entries.forEach { e ->
        val own = myWorkerId != null && e.workerId == myWorkerId
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(e.workerName, durationLabel(e.minutes(now))).joinToString(" \u00b7 "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatInstant(e.start) + (e.end?.let { " \u2013 " + formatInstant(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = timeStatusLabel(e),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (e.status == TimeEntry.STATUS_REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    e.rejectReason?.takeIf { e.status == TimeEntry.STATUS_REJECTED }?.let {
                        Text(stringResource(R.string.wo_time_reject_reason_value, it), style = MaterialTheme.typography.bodySmall)
                    }
                    e.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (own && e.isEditable) {
                    IconButton(enabled = enabled, onClick = { editing = e }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.wo_time_edit))
                    }
                    IconButton(enabled = enabled, onClick = { onDelete(e.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.wo_time_delete))
                    }
                }
            }
            if (canApprove && e.isDecidable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.status != TimeEntry.STATUS_REJECTED) {
                        TextButton(enabled = enabled, onClick = { rejecting = e }) { Text(stringResource(R.string.wo_reject)) }
                    }
                    TextButton(enabled = enabled, onClick = { onApprove(e.id) }) { Text(stringResource(R.string.wo_approve)) }
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
    }
    if (myWorkerId != null) {
        TextButton(enabled = enabled, onClick = { creating = true }) { Text(stringResource(R.string.wo_time_log)) }
    }

    if (creating) {
        TimeEntryDialog(initial = null, onDismiss = { creating = false }, onSave = { onLog(it); creating = false })
    }
    editing?.let { e ->
        TimeEntryDialog(initial = e, onDismiss = { editing = null }, onSave = { onUpdate(e.id, it); editing = null })
    }
    rejecting?.let { e ->
        RejectReasonDialog(onDismiss = { rejecting = null }, onReject = { onReject(e.id, it); rejecting = null })
    }
}

@Composable
private fun timeStatusLabel(e: TimeEntry): String = stringResource(
    when {
        e.isRunning -> R.string.wo_time_status_running
        e.status == TimeEntry.STATUS_APPROVED -> R.string.wo_time_status_approved
        e.status == TimeEntry.STATUS_REJECTED -> R.string.wo_time_status_rejected
        else -> R.string.wo_time_status_submitted
    },
)

@Composable
private fun TimeEntryDialog(initial: TimeEntry?, onDismiss: () -> Unit, onSave: (TimeEntryDraft) -> Unit) {
    val defaultEnd = remember { System.currentTimeMillis() / 60_000L * 60_000L }
    var start by remember { mutableLongStateOf(initial?.start ?: (defaultEnd - 3_600_000L)) }
    var end by remember { mutableLongStateOf(initial?.end ?: defaultEnd) }
    var breakText by remember { mutableStateOf((initial?.breakMinutes ?: 0).toString()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    val breakMinutes = breakText.toIntOrNull()
    val spanOk = end > start
    val breakOk = breakMinutes != null && breakMinutes >= 0 && breakMinutes * 60_000L < end - start
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.wo_time_log else R.string.wo_time_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WoDateTimeField(label = stringResource(R.string.wo_time_start), valueMillis = start, onChange = { start = it })
                WoDateTimeField(label = stringResource(R.string.wo_time_end), valueMillis = end, onChange = { end = it })
                if (!spanOk) {
                    Text(stringResource(R.string.wo_time_end_before_start), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = breakText,
                    onValueChange = { breakText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.wo_time_break)) },
                    isError = spanOk && !breakOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.wo_time_notes)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = spanOk && breakOk,
                onClick = { onSave(TimeEntryDraft(start, end, breakMinutes ?: 0, notes.trim().ifBlank { null })) },
            ) { Text(stringResource(R.string.wo_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wo_cancel)) } },
    )
}

@Composable
private fun RejectReasonDialog(onDismiss: () -> Unit, onReject: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wo_time_reject_title)) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.wo_time_reject_reason)) },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = reason.isNotBlank(), onClick = { onReject(reason) }) { Text(stringResource(R.string.wo_reject)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wo_cancel)) } },
    )
}

/** Audit trail of the work order (online read). */
@Composable
internal fun HistoryContent(entries: List<WorkOrderAuditEntry>) {
    if (entries.isEmpty()) {
        Text(
            stringResource(R.string.wo_history_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    entries.forEach { e ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                text = e.description?.takeIf { it.isNotBlank() } ?: e.action_type.orEmpty().replace('_', ' '),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatIso(e.created_at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Budgets covering the work order with their spend (online read). */
@Composable
internal fun BudgetsCard(budgets: List<WorkOrderBudget>, modifier: Modifier = Modifier) {
    if (budgets.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wo_budget_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            budgets.forEach { b ->
                val over = b.percent_used > 100.0
                Column {
                    Text(b.name, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { (b.percent_used / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        stringResource(
                            R.string.wo_budget_used,
                            Formatters.formatCurrency(b.actual, b.currency),
                            Formatters.formatCurrency(b.amount, b.currency),
                            b.percent_used.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
