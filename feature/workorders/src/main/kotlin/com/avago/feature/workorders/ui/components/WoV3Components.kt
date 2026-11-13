package com.avago.feature.workorders.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.SlaLevel
import com.avago.feature.workorders.model.SlaPhase
import com.avago.feature.workorders.model.SlaWindow
import com.avago.feature.workorders.model.WO_HOLD_REASONS
import com.avago.feature.workorders.model.WoHold
import com.avago.feature.workorders.model.WoVendor
import com.avago.feature.workorders.model.parseWoAttributes
import com.avago.feature.workorders.model.slaState
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Localized label for a WO status key (accepts the legacy "complete" alias). */
@Composable
fun woStatusLabel(status: String?): String = when (status?.lowercase()) {
    "draft" -> stringResource(R.string.wo_status_draft)
    "open" -> stringResource(R.string.wo_status_open)
    "pending_review" -> stringResource(R.string.wo_status_pending_review)
    "reviewed" -> stringResource(R.string.wo_status_reviewed)
    "assigned" -> stringResource(R.string.wo_status_assigned)
    "in_progress" -> stringResource(R.string.wo_status_in_progress)
    "on_hold" -> stringResource(R.string.wo_status_on_hold)
    "completed", "complete" -> stringResource(R.string.wo_status_complete)
    "cancelled" -> stringResource(R.string.wo_status_cancelled)
    null -> ""
    else -> status.replace('_', ' ').replaceFirstChar { it.titlecase() }
}

private fun formatDuration(ms: Long): String {
    val mins = abs(ms) / 60_000
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/** SLA chip (contract §2): respond / resolve countdown, warn at 75 %, breach when overdue. */
@Composable
fun WoSlaChip(wo: WorkOrderEntity, policy: Map<String, SlaWindow>, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(wo.woId) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val state = remember(wo.status, wo.priority, wo.createdAt, wo.completedAt, wo.attributes, policy, now) {
        slaState(wo.status, wo.priority, wo.createdAt, wo.completedAt, parseWoAttributes(wo.attributes), policy, now)
    }
    if (state.phase == SlaPhase.NONE) return
    val color = when (state.level) {
        SlaLevel.OK -> Color(0xFF16A34A)
        SlaLevel.WARN -> Color(0xFFD97706)
        SlaLevel.BREACH -> Color(0xFFDC2626)
    }
    val remaining = state.remainingMs
    val text = when {
        state.phase == SlaPhase.MET && state.level == SlaLevel.BREACH -> stringResource(R.string.wo_sla_missed)
        state.phase == SlaPhase.MET -> stringResource(R.string.wo_sla_met)
        remaining != null && remaining < 0 -> stringResource(
            if (state.phase == SlaPhase.RESPOND) R.string.wo_sla_respond_overdue else R.string.wo_sla_resolve_overdue,
            formatDuration(remaining),
        )
        else -> stringResource(
            if (state.phase == SlaPhase.RESPOND) R.string.wo_sla_respond_in else R.string.wo_sla_resolve_in,
            formatDuration(remaining ?: 0),
        )
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun holdReasonLabel(reason: String): String = when (reason) {
    "parts" -> stringResource(R.string.wo_hold_reason_parts)
    "vendor" -> stringResource(R.string.wo_hold_reason_vendor)
    "approval" -> stringResource(R.string.wo_hold_reason_approval)
    "customer" -> stringResource(R.string.wo_hold_reason_customer)
    else -> stringResource(R.string.wo_hold_reason_other)
}

/** "Waiting on" hold sub-state (contract §2 `hold`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WoHoldEditor(
    hold: WoHold?,
    enabled: Boolean,
    onSet: (reason: String, note: String?) -> Unit,
    onClear: () -> Unit,
) {
    var note by rememberSaveable(hold?.note) { mutableStateOf(hold?.note.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (hold != null) stringResource(R.string.wo_hold_waiting_on, holdReasonLabel(hold.reason))
            else stringResource(R.string.wo_hold_title),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WO_HOLD_REASONS.forEach { r ->
                FilterChip(
                    selected = hold?.reason == r,
                    enabled = enabled,
                    onClick = { onSet(r, note.trim().ifBlank { null }) },
                    label = { Text(holdReasonLabel(r)) },
                )
            }
        }
        if (hold != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.wo_hold_note)) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = enabled, onClick = { onSet(hold.reason, note.trim().ifBlank { null }) }) {
                    Text(stringResource(R.string.wo_save))
                }
            }
            TextButton(enabled = enabled, onClick = onClear) { Text(stringResource(R.string.wo_hold_clear)) }
        }
    }
}

/** Vendor / PO / estimate / invoice fields (contract §2 `vendor`). */
@Composable
fun WoVendorEditor(vendor: WoVendor, enabled: Boolean, onSave: (WoVendor) -> Unit) {
    var name by rememberSaveable(vendor.vendorName) { mutableStateOf(vendor.vendorName.orEmpty()) }
    var po by rememberSaveable(vendor.poId) { mutableStateOf(vendor.poId.orEmpty()) }
    var estimate by rememberSaveable(vendor.estimateTotal) { mutableStateOf(vendor.estimateTotal?.toString().orEmpty()) }
    var invoiceRef by rememberSaveable(vendor.invoiceRef) { mutableStateOf(vendor.invoiceRef.orEmpty()) }
    var invoiceTotal by rememberSaveable(vendor.invoiceTotal) { mutableStateOf(vendor.invoiceTotal?.toString().orEmpty()) }
    fun money(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.wo_vendor_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name, onValueChange = { name = it }, enabled = enabled, singleLine = true,
            label = { Text(stringResource(R.string.wo_vendor_name)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = po, onValueChange = { po = it }, enabled = enabled, singleLine = true,
            label = { Text(stringResource(R.string.wo_vendor_po)) }, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = estimate, onValueChange = { estimate = it }, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.wo_vendor_estimate)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = invoiceTotal, onValueChange = { invoiceTotal = it }, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.wo_vendor_invoice_total)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = invoiceRef, onValueChange = { invoiceRef = it }, enabled = enabled, singleLine = true,
            label = { Text(stringResource(R.string.wo_vendor_invoice_ref)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            enabled = enabled,
            onClick = {
                val trimmedName = name.trim().ifBlank { null }
                onSave(
                    vendor.copy(
                        vendorName = trimmedName,
                        vendorId = vendor.vendorId.takeIf { trimmedName == vendor.vendorName },
                        poId = po.trim().ifBlank { null },
                        estimateTotal = money(estimate),
                        invoiceRef = invoiceRef.trim().ifBlank { null },
                        invoiceTotal = money(invoiceTotal),
                    ),
                )
            },
        ) { Text(stringResource(R.string.wo_save)) }
    }
}
