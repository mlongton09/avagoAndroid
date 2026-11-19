package com.avago.feature.rentals.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.BulkCountsDraft
import com.avago.core.data.domain.RentalBulkReturn
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.rental.Money
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.design.theme.AvagoTheme
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.cardListItemColors
import com.avago.feature.assets.ui.generateQrBitmap
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalsOnline
import com.avago.feature.rentals.domain.Bulk
import com.avago.feature.rentals.domain.CreditCheck
import com.avago.feature.rentals.domain.Esign
import com.avago.feature.rentals.domain.HandoverGates
import com.avago.feature.rentals.domain.Kits
import com.avago.feature.rentals.domain.LineView
import com.avago.feature.rentals.domain.OnlinePayments
import com.avago.feature.rentals.domain.OrderStatus
import com.avago.feature.rentals.domain.OrderView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Wave 2 additions to the order screen: hand-over gate fixes (G1/G2), agreement status (G1),
 * credit / documents (G2), the order's online pay links (G6, read-only) and bulk returns (G3).
 * Online answers are GET-only and cached in memory; nothing here blocks the order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderWave2ViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val online: RentalsOnline,
    private val permissions: PermissionsManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val orderId: String = requireNotNull(savedState["orderId"])

    private val order: StateFlow<OrderView?> = repo.observeOrder(orderId)
        .map { it?.let(OrderView::of) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun can(permission: String?): Boolean = permission != null && permissions.can(permission)

    // ── Gate fixes ──

    data class GateItem(val commandId: String, val gate: HandoverGates.Gate)

    /** The newest hand-over of this order the server refused at a gate, if any. */
    val gate: StateFlow<GateItem?> = repo.observeFailures(orderId)
        .map { fs -> fs.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { f -> HandoverGates.of(f)?.let { GateItem(f.commandId, it) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Online status (cached) ──

    val esign: StateFlow<Esign.Status?> = combine(
        online.observe(online.signaturesKey(orderId)),
        repo.observeSignatures(orderId),
    ) { c, local -> Esign.parse(c?.data)?.let { Esign.withLocal(it, local) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val credit: StateFlow<CreditCheck?> = online.observe(online.creditKey(orderId))
        .map { CreditCheck.parse(it?.data) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null unless the account has online payments switched on. */
    val payments: StateFlow<OnlinePayments.OrderPayments?> = combine(
        online.observe(online.paymentsStatusKey).map { OnlinePayments.enabled(it?.data) },
        order,
        online.observe(online.orderPaymentsKey(orderId)),
    ) { enabled, o, c -> if (enabled) OnlinePayments.parseOrder(c?.data, o?.currency) else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Why online payments are off (null while on or unknown): manual recording stays available. */
    val paymentUnavailable: StateFlow<OnlinePayments.Unavailable?> = online.observe(online.paymentsStatusKey)
        .map { OnlinePayments.unavailable(it?.data) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Bulk returns ──

    val bulkReturns: StateFlow<Map<String, List<RentalBulkReturn>>> = repo.observeBulkReturns(orderId)
        .map { rows -> rows.groupBy { it.orderLineId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ── Events ──

    enum class Msg { FIX_INCOMPLETE, OFFLINE, FAILED, BULK_SAVED }

    sealed interface Event {
        data class Message(val msg: Msg, val detail: String? = null) : Event
        data class Share(val url: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "order wave2 action failed")
            _events.tryEmit(Event.Message(Msg.FAILED, e.message))
        } finally {
            _busy.value = false
        }
    }

    init {
        viewModelScope.launch { refresh(order.filterNotNull().first()) }
    }

    /** Screen open / pull-to-refresh: online answers for this order (failures keep the cache). */
    fun refresh() {
        order.value?.let { o -> viewModelScope.launch { refresh(o) } }
    }

    private suspend fun refresh(o: OrderView) {
        if (online.refreshSignatures(o.id) is RentalsOnline.Result.Offline) return
        if (o.status !in CLOSED) {
            val open = repo.observeLines(o.id).first()
                .filter { Kits.isHandoverLine(it.kind) && it.status == "open" }
                .map { it.id }
            if (open.isNotEmpty()) online.refreshCreditCheck(o.id, open)
        }
        val status = online.refreshPaymentsStatus()
        if (status is RentalsOnline.Result.Ok && OnlinePayments.enabled(status.value)) online.refreshOrderPayments(o.id)
    }

    /** Resends the refused hand-over with the user's override / reading. */
    fun fixGate(item: GateItem, reason: String?, meterValue: String?) = launchSafe {
        val fix = HandoverGates.override(item.gate, reason, meterValue)
        if (fix == null) _events.emit(Event.Message(Msg.FIX_INCOMPLETE)) else repo.retryHandover(item.commandId, fix)
    }

    /** Resends the refused hand-over unchanged (after the cause was fixed, e.g. the agreement signed). */
    fun retryGate(item: GateItem) = launchSafe { repo.retryHandover(item.commandId, null) }

    /** Drops the refused hand-over; its optimistic change is rolled back. */
    fun discardGate(item: GateItem) = launchSafe { repo.dismissFailure(item.commandId) }

    fun bulkReturn(line: LineView, counts: Bulk.Counts, notes: String) = launchSafe {
        repo.bulkReturn(
            orderId, line.id,
            BulkCountsDraft(returned = counts.returned, damaged = counts.damaged, lost = counts.lost, notes = notes.trim().ifEmpty { null }),
            Instant.now(), null,
        )
        _events.emit(Event.Message(Msg.BULK_SAVED))
    }

    companion object {
        private val CLOSED = setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.RETURNED)
    }
}

// ── Gate card ────────────────────────────────────────────────────────────────────────

/** Localised text of a hand-over gate (the server message is shown below when it differs). */
@Composable
internal fun gateText(g: HandoverGates.Gate): String = when {
    g.code == HandoverGates.SIGNATURE_REQUIRED -> stringResource(R.string.rentals_gate_signature)
    g.code == HandoverGates.METER_REQUIRED -> stringResource(R.string.rentals_gate_meter_required)
    g.code == HandoverGates.METER_BELOW_CHECKOUT -> stringResource(R.string.rentals_meter_below_checkout)
    g.code == HandoverGates.METER_DELTA_EXCEEDED -> stringResource(R.string.rentals_gate_meter_delta)
    g.code == HandoverGates.REASON_REQUIRED -> stringResource(R.string.rentals_gate_reason_required)
    g.isCredit -> creditBlockLabel(g.code)
    else -> g.message ?: g.code
}

private fun HandoverGates.Gate.isDocuments(): Boolean = isCredit && code.contains("document")

/**
 * A queued hand-over the server refused at a gate: what happened and the one fix (sign now,
 * add the document, enter the reading, or override with a reason when permitted). Retry and
 * discard stay secondary; discard is red and confirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderGateCard(
    gate: HandoverGates.Gate,
    canOverride: Boolean,
    busy: Boolean,
    onSign: (() -> Unit)?,
    onAddDocument: (() -> Unit)?,
    onFix: (reason: String?, meter: String?) -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val signFix = gate.code == HandoverGates.SIGNATURE_REQUIRED && onSign != null
    val docFix = gate.isDocuments() && onAddDocument != null
    SectionCard(
        title = stringResource(R.string.rentals_gate_title),
        trailing = { StatusPill(stringResource(R.string.rentals_gate_stopped), StatusTone.Danger, icon = Icons.Default.Warning) },
        contentPadding = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            val text = gateText(gate)
            Text(text, style = MaterialTheme.typography.bodyLarge)
            gate.message?.takeIf { it != text }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                when {
                    signFix -> Button(onClick = { onSign?.invoke() }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Default.Draw, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_sign_agreement_now), Modifier.padding(start = AvagoSpacing.sm))
                    }
                    gate.needsReading -> Button(onClick = { dialog = true }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_gate_enter_reading))
                    }
                    docFix -> Button(onClick = { onAddDocument?.invoke() }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Default.Badge, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_add_document), Modifier.padding(start = AvagoSpacing.sm))
                    }
                    canOverride -> Button(onClick = { dialog = true }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_gate_override))
                    }
                }
                if (canOverride && !gate.needsReading && (signFix || docFix)) {
                    OutlinedButton(onClick = { dialog = true }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_gate_override))
                    }
                }
                TextButton(onClick = onRetry, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_gate_retry))
                }
                TextButton(onClick = { confirmDiscard = true }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_gate_discard), color = MaterialTheme.colorScheme.error)
                }
            }
            if (!canOverride && !gate.needsReading) {
                Text(
                    stringResource(R.string.rentals_override_ask_manager),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (dialog) {
        GateFixDialog(gate, onDismiss = { dialog = false }) { reason, meter ->
            dialog = false
            onFix(reason, meter)
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.rentals_gate_discard_title)) },
            text = { Text(stringResource(R.string.rentals_gate_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscard() }) {
                    Text(stringResource(R.string.rentals_gate_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.rentals_gate_keep)) } },
        )
    }
}

@Composable
private fun GateFixDialog(gate: HandoverGates.Gate, onDismiss: () -> Unit, onConfirm: (reason: String?, meter: String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val reading = gate.needsReading
    val needsText = reading || gate.needsReason
    val valid = when {
        reading -> text.replace(',', '.').trim().toBigDecimalOrNull()?.let { it.signum() >= 0 } == true
        needsText -> text.isNotBlank()
        else -> true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (reading) R.string.rentals_gate_enter_reading else R.string.rentals_gate_override)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                Text(gateText(gate), style = MaterialTheme.typography.bodyMedium)
                if (needsText) {
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text(stringResource(if (reading) R.string.rentals_gate_reading else R.string.rentals_override_reason)) },
                        keyboardOptions = if (reading) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                        singleLine = reading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!reading) {
                    Text(
                        stringResource(R.string.rentals_gate_override_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.takeIf { !reading }, text.takeIf { reading }) }, enabled = valid) {
                Text(stringResource(R.string.rentals_gate_resend))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_gate_cancel)) } },
    )
}

// ── Agreement / credit cards ─────────────────────────────────────────────────────────

/** Agreement status on the order (hidden while unknown or when no signature is involved). */
@Composable
internal fun OrderAgreementCard(esign: Esign.Status?, onSign: (() -> Unit)?) {
    esign ?: return
    if (esign.requests.isEmpty() && !esign.requireBeforeCheckout && !esign.agreementSigned) return
    val local = esign.requests.any { r -> r.signers.any { it.status == Esign.STATUS_SIGNED_LOCAL } }
    val open = !esign.agreementSigned && !local
    SectionCard(
        title = stringResource(R.string.rentals_agreement),
        trailing = {
            when {
                esign.agreementSigned -> StatusPill(stringResource(R.string.rentals_agreement_signed), StatusTone.Success, icon = Icons.Default.CheckCircle)
                local -> StatusPill(stringResource(R.string.rentals_agreement_signed_syncing), StatusTone.Info, icon = Icons.Default.Schedule)
                else -> StatusPill(
                    stringResource(R.string.rentals_agreement_unsigned),
                    if (esign.blocksCheckout) StatusTone.Warning else StatusTone.Neutral,
                )
            }
        },
        contentPadding = open,
    ) {
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                if (esign.requireBeforeCheckout) {
                    Text(
                        stringResource(R.string.rentals_agreement_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onSign != null) {
                    FilledTonalButton(onClick = onSign, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Default.Draw, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_sign_agreement_now), Modifier.padding(start = AvagoSpacing.sm))
                    }
                }
            }
        }
    }
}

/** Credit / document status before check-out (G2); hidden when nothing stands in the way. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderCreditCard(credit: CreditCheck?, onAddDocument: (() -> Unit)?) {
    credit ?: return
    val docs = credit.missingDocuments
    if (credit.allowed && docs.isEmpty()) return
    SectionCard(
        title = stringResource(R.string.rentals_credit_status),
        trailing = if (!credit.allowed) {
            { StatusPill(stringResource(R.string.rentals_credit_blocked), StatusTone.Danger, icon = Icons.Default.Warning) }
        } else {
            null
        },
        contentPadding = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            credit.blocks.forEach { b ->
                Text("• " + (b.message ?: creditBlockLabel(b.code)), style = MaterialTheme.typography.bodyMedium)
            }
            if (docs.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    docs.forEach { d ->
                        StatusPill(
                            stringResource(
                                if (d.status == "expired") R.string.rentals_doc_expired else R.string.rentals_doc_missing,
                                stringResource(documentKindLabel(d.kind)),
                            ),
                            StatusTone.Danger,
                            icon = Icons.Default.Badge,
                        )
                    }
                }
                if (onAddDocument != null) {
                    OutlinedButton(onClick = onAddDocument, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Default.Badge, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_add_document), Modifier.padding(start = AvagoSpacing.sm))
                    }
                }
            }
        }
    }
}

// ── Online payments (G6) ─────────────────────────────────────────────────────────────

private fun payStatusTone(r: OnlinePayments.PayRequest): StatusTone = when {
    r.status == "paid" -> StatusTone.Success
    r.needsAction -> StatusTone.Warning
    r.isOpen -> StatusTone.Info
    else -> StatusTone.Neutral
}

@Composable
private fun payStatusText(r: OnlinePayments.PayRequest): String = when {
    r.status == "paid" -> stringResource(R.string.rentals_pay_status_paid)
    r.needsAction -> stringResource(R.string.rentals_pay_status_action)
    r.isOpen -> stringResource(R.string.rentals_pay_status_open)
    r.status == "expired" -> stringResource(R.string.rentals_pay_status_expired)
    r.status == "canceled" || r.status == "cancelled" -> stringResource(R.string.rentals_pay_status_canceled)
    else -> r.status
}

/**
 * Pay links of the order (read-only; requesting a link is not in the mobile command catalog).
 * Only composed when online payments are enabled for the account.
 */
@Composable
internal fun OnlinePaymentsCard(
    payments: OnlinePayments.OrderPayments,
    zone: ZoneId,
    onShare: (String) -> Unit,
) {
    var qr by rememberSaveable { mutableStateOf<String?>(null) }
    if (payments.requests.isEmpty()) return
    SectionCard(title = stringResource(R.string.rentals_pay_links)) {
        payments.requests.forEachIndexed { i, r ->
            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
            ListItem(
                colors = cardListItemColors(),
                leadingContent = { LeadingBadge(Icons.Default.Link, payStatusTone(r)) },
                headlineContent = { Text(formatMoney(r.amount)) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            payStatusText(r),
                            r.expiresAt?.takeIf { r.isOpen }?.let { stringResource(R.string.rentals_pay_expires, formatDateTime(it, zone)) },
                        ).joinToString(" \u00b7 "),
                    )
                },
                trailingContent = if (r.shareable) {
                    {
                        OverflowMenu(
                            listOf(
                                MenuAction(stringResource(R.string.rentals_pay_share), Icons.Default.Share) { r.payUrl?.let(onShare) },
                                MenuAction(stringResource(R.string.rentals_pay_qr), Icons.Default.QrCode2) { qr = r.payUrl },
                            ),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
    qr?.let { url ->
        val bmp = remember(url) { generateQrBitmap(url) }
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text(stringResource(R.string.rentals_pay_qr)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.rentals_pay_qr), Modifier.size(240.dp)) }
                    Text(
                        stringResource(R.string.rentals_pay_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = AvagoSpacing.sm),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qr = null }) { Text(stringResource(R.string.rentals_pay_close)) } },
        )
    }
}

internal fun sharePayLink(context: Context, url: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, title))
}

// \u2500\u2500 Lines (kits, bulk) \u2500\u2500

private fun qtyText(q: BigDecimal): String = q.stripTrailingZeros().toPlainString()

/** A bulk line with something left to count back in (null for serialized lines). */
internal fun bulkOutstanding(l: LineView, returns: Map<String, List<RentalBulkReturn>>): BigDecimal? =
    if (!l.isBulk) null else Bulk.outstanding(l.qty, returns[l.id].orEmpty())

/**
 * The order's lines grouped by kit (`kit_instance_id`; header first, components indented) and
 * \u2014 for bulk lines out on rent \u2014 a count return.
 */
@Composable
internal fun OrderLinesCard(
    lines: List<LineView>,
    zone: ZoneId,
    bulkReturns: Map<String, List<RentalBulkReturn>>,
    onBulkReturn: ((LineView, BigDecimal) -> Unit)?,
) {
    SectionCard(title = stringResource(R.string.rentals_lines)) {
        if (lines.isEmpty()) CardNote(stringResource(R.string.rentals_handover_no_unit))
        var first = true
        Kits.group(lines, { it.kitInstanceId }, { it.isKitHeader }).forEach { g ->
            g.header?.let { h ->
                if (!first) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                first = false
                ListItem(
                    colors = cardListItemColors(),
                    leadingContent = { LeadingBadge(Icons.Default.Widgets, StatusTone.Info) },
                    overlineContent = { Text(stringResource(R.string.rentals_kit)) },
                    headlineContent = { Text(h.description ?: stringResource(R.string.rentals_kit), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                h.status?.let { lineStatusLabel(it) }?.let { stringResource(it) },
                                pluralStringResource(R.plurals.rentals_kit_components, g.components.size, g.components.size),
                            ).joinToString(" \u00b7 "),
                        )
                    },
                    trailingContent = { Text(formatMoney(h.amount), style = MaterialTheme.typography.bodyMedium) },
                )
            }
            val indent = g.header != null || g.components.size > 1
            g.components.forEach { l ->
                if (!first) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                first = false
                val outstanding = bulkOutstanding(l, bulkReturns)
                val canReturn = onBulkReturn != null && l.status == "out" && outstanding != null && outstanding.signum() > 0
                LineRow(
                    l, zone,
                    indent = indent && l.kitInstanceId != null,
                    outstanding = outstanding?.takeIf { l.status == "out" },
                    onBulkReturn = if (canReturn && outstanding != null) ({ onBulkReturn?.invoke(l, outstanding) }) else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineRow(
    l: LineView,
    zone: ZoneId,
    indent: Boolean,
    outstanding: BigDecimal?,
    onBulkReturn: (() -> Unit)?,
) {
    val statusText = l.status?.let { lineStatusLabel(it) }?.let { stringResource(it) }
    ListItem(
        colors = cardListItemColors(),
        modifier = if (indent) Modifier.padding(start = AvagoSpacing.lg) else Modifier,
        leadingContent = {
            UnitThumbnail(l.assetId, Icons.Default.Inventory2, if (l.status == "out") StatusTone.Accent else StatusTone.Neutral)
        },
        headlineContent = { Text(l.description ?: l.kind, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                Text(
                    listOfNotNull(
                        statusText,
                        "${qtyText(l.qty)} \u00d7",
                        "${formatDateTime(l.startAt, zone)} \u2192 ${formatDateTime(l.endAt, zone)}",
                    ).joinToString(" \u00b7 "),
                )
                outstanding?.let {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                        StatusPill(stringResource(R.string.rentals_bulk_outstanding, it.stripTrailingZeros().toPlainString()), StatusTone.Warning)
                    }
                }
                if (onBulkReturn != null) {
                    TextButton(onClick = onBulkReturn, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.AutoMirrored.Filled.AssignmentReturn, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.rentals_bulk_return), Modifier.padding(start = AvagoSpacing.sm))
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(l.amount), style = MaterialTheme.typography.bodyMedium)
                SyncStateBadge(l.sync)
            }
        },
    )
}

/** Returned / damaged / lost counts for a bulk line (prefilled: returned = outstanding). */
@Composable
internal fun BulkReturnDialog(
    line: LineView,
    outstanding: BigDecimal,
    onDismiss: () -> Unit,
    onConfirm: (Bulk.Counts, String) -> Unit,
) {
    val out = outstanding.stripTrailingZeros().toPlainString()
    var returned by rememberSaveable { mutableStateOf(out) }
    var damaged by rememberSaveable { mutableStateOf("") }
    var lost by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val r = Bulk.parseQty(returned)
    val d = Bulk.parseQty(damaged)
    val l = Bulk.parseQty(lost)
    val counts = if (r != null && d != null && l != null) Bulk.Counts(r, d, l) else null
    val check = counts?.let { Bulk.check(it, outstanding) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rentals_bulk_return)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                Text(stringResource(R.string.rentals_bulk_return_body, line.description ?: line.kind, out), style = MaterialTheme.typography.bodyMedium)
                QtyField(returned, R.string.rentals_bulk_returned) { returned = it }
                QtyField(damaged, R.string.rentals_bulk_damaged) { damaged = it }
                QtyField(lost, R.string.rentals_bulk_lost) { lost = it }
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.rentals_bulk_notes)) }, modifier = Modifier.fillMaxWidth())
                val error = when (check) {
                    null, Bulk.Check.NEGATIVE -> stringResource(R.string.rentals_bulk_invalid)
                    Bulk.Check.EXCEEDS -> stringResource(R.string.rentals_bulk_exceeds, out)
                    else -> null
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { counts?.let { onConfirm(it, notes) } }, enabled = check == Bulk.Check.OK) {
                Text(stringResource(R.string.rentals_bulk_return_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_gate_cancel)) } },
    )
}

@Composable
private fun QtyField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Order wave 2 – light", showBackground = true, widthDp = 380, heightDp = 1100)
@Preview(name = "Order wave 2 – dark", showBackground = true, widthDp = 380, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Order wave 2 – 200% font", showBackground = true, widthDp = 380, heightDp = 1800, fontScale = 2f)
@Preview(name = "Order wave 2 – German", showBackground = true, widthDp = 380, heightDp = 1100, locale = "de")
@Composable
private fun OrderWave2Preview() {
    val zone = ZoneId.of("UTC")
    AvagoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                OrderGateCard(
                    HandoverGates.Gate(HandoverGates.SIGNATURE_REQUIRED, Esign.PERMISSION_OVERRIDE, null),
                    canOverride = true, busy = false, onSign = {}, onAddDocument = null, onFix = { _, _ -> }, onRetry = {}, onDiscard = {},
                )
                OnlinePaymentsCard(
                    OnlinePayments.OrderPayments(
                        enabled = true,
                        requests = listOf(
                            OnlinePayments.PayRequest("p1", "open", null, "balance", Money.of(12_500, "EUR"), "https://pay.example/x", true, null),
                        ),
                    ),
                    zone = zone, onShare = {},
                )
                OrderLinesCard(
                    lines = listOf(
                        LineView("k", "o", "kit", null, null, "PA kit", BigDecimal.ONE, null, null, Money.of(30_000, "EUR"), null, "out", kitInstanceId = "ki"),
                        LineView("c1", "o", "rental", null, "a1", "Speaker 12\"", BigDecimal(2), null, null, null, null, "out", kitInstanceId = "ki"),
                        LineView("b1", "o", "accessory", null, null, "Traffic cone", BigDecimal(20), null, null, Money.of(4_000, "EUR"), null, "out", isBulk = true),
                    ),
                    zone = zone,
                    bulkReturns = emptyMap(),
                    onBulkReturn = { _, _ -> },
                )
            }
        }
    }
}
