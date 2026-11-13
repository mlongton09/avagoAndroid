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
import com.avago.feature.workorders.model.WoVendorMath
import com.avago.feature.workorders.model.parseWoAttributes
import com.avago.feature.workorders.model.slaState
import kotlinx.coroutines.delay
import kotlin.math.abs
import java.text.NumberFormat
import java.util.Currency
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction

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
    val estimateInvalid = estimate.isNotBlank() && money(estimate) == null
    val invoiceInvalid = invoiceTotal.isNotBlank() && money(invoiceTotal) == null
    val invalidMsg = stringResource(R.string.wo_vendor_invalid_amount)
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
                isError = estimateInvalid,
                supportingText = if (estimateInvalid) { { Text(invalidMsg) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = invoiceTotal, onValueChange = { invoiceTotal = it }, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.wo_vendor_invoice_total)) },
                isError = invoiceInvalid,
                supportingText = if (invoiceInvalid) { { Text(invalidMsg) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = invoiceRef, onValueChange = { invoiceRef = it }, enabled = enabled, singleLine = true,
            label = { Text(stringResource(R.string.wo_vendor_invoice_ref)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            enabled = enabled && !estimateInvalid && !invoiceInvalid,
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

private fun formatMoney(amount: Double, currency: String?): String {
    val fmt = NumberFormat.getCurrencyInstance()
    runCatching { fmt.currency = Currency.getInstance(currency?.uppercase() ?: "USD") }
    return fmt.format(amount)
}

/**
 * Estimate vs actual (web `EstimateVsActual`, W5). Actual = vendor invoice, else
 * cost-line total, else WO total. Variance is colored under / on / over (?5 %).
 * Hidden when there is neither an estimate nor an invoice.
 */
@Composable
fun WoEstimateVsActual(
    vendor: WoVendor,
    linesTotal: Double?,
    woTotal: Double?,
    currency: String?,
    modifier: Modifier = Modifier,
) {
    val estimate = vendor.estimateTotal
    if (estimate == null && vendor.invoiceTotal == null) return
    val actual = WoVendorMath.actualCost(vendor.invoiceTotal, linesTotal, woTotal)
    val variance = actual?.let { WoVendorMath.costVariance(estimate, it.amount) }
    val linesVsInvoice = if (vendor.invoiceTotal != null && (linesTotal ?: 0.0) > 0) {
        WoVendorMath.costVariance(linesTotal, vendor.invoiceTotal)
    } else null

    val levelColor = when (variance?.level) {
        WoVendorMath.Level.UNDER -> Color(0xFF15803D)
        WoVendorMath.Level.OVER -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val levelLabel = when (variance?.level) {
        WoVendorMath.Level.UNDER -> stringResource(R.string.wo_cost_level_under)
        WoVendorMath.Level.ON -> stringResource(R.string.wo_cost_level_on)
        WoVendorMath.Level.OVER -> stringResource(R.string.wo_cost_level_over)
        null -> ""
    }
    val sourceLabel = when (actual?.source) {
        WoVendorMath.ActualSource.INVOICE -> stringResource(R.string.wo_cost_source_invoice)
        WoVendorMath.ActualSource.LINES -> stringResource(R.string.wo_cost_source_lines)
        WoVendorMath.ActualSource.WO -> stringResource(R.string.wo_cost_source_wo)
        null -> ""
    }
    val dash = "\u2014"
    val estimateText = estimate?.let { formatMoney(it, currency) } ?: dash
    val actualText = actual?.let { formatMoney(it.amount, currency) } ?: dash
    val diffText = variance?.let { (if (it.diff > 0) "+" else "") + formatMoney(it.diff, currency) } ?: dash
    val pctText = variance?.pct?.let { (if (it > 0) "+" else "") + "%.1f".format(it) + " %" } ?: dash

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.wo_cost_estimate_vs_actual),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CostCell(stringResource(R.string.wo_cost_estimate), estimateText, null, Modifier.weight(1f))
            CostCell(
                stringResource(R.string.wo_cost_actual),
                actualText,
                sourceLabel.takeIf { actual != null },
                Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    if (variance != null) contentDescription = "$diffText, $pctText, $levelLabel"
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CostCell(stringResource(R.string.wo_cost_variance), diffText, null, Modifier.weight(1f), levelColor)
            CostCell(stringResource(R.string.wo_cost_variance_pct), pctText, levelLabel.ifEmpty { null }, Modifier.weight(1f), levelColor)
        }
        if (linesVsInvoice != null && linesVsInvoice.level != WoVendorMath.Level.ON) {
            Text(
                text = stringResource(
                    R.string.wo_cost_invoice_mismatch,
                    formatMoney(linesTotal ?: 0.0, currency),
                    formatMoney(vendor.invoiceTotal ?: 0.0, currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92400E),
            )
        }
    }
}

@Composable
private fun CostCell(
    label: String,
    value: String,
    hint: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
