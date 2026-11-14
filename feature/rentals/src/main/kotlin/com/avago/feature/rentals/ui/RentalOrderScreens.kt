package com.avago.feature.rentals.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.LineView
import com.avago.feature.rentals.domain.OrderStatus
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.long
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.str
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

// ── Orders list ──────────────────────────────────────────────────────────────────────

@HiltViewModel
class RentalOrdersViewModel @Inject constructor(repo: RentalRepository) : ViewModel() {
    val activeOnly = MutableStateFlow(true)
    val query = MutableStateFlow("")
    val orders: StateFlow<List<OrderView>> = combine(repo.records(RentalEntity.ORDER), activeOnly, query) { rows, active, q ->
        rows.map(OrderView::of)
            .filter { !active || it.status !in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED) }
            .filter { q.isBlank() || it.number?.contains(q, true) == true || it.customerName?.contains(q, true) == true }
            .sortedWith(compareByDescending<OrderView> { it.startAt ?: Instant.MIN })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RentalOrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onQuickBook: () -> Unit,
    viewModel: RentalOrdersViewModel = hiltViewModel(),
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val active by viewModel.activeOnly.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_orders), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onQuickBook, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.rentals_quick_book)) })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    query, { viewModel.query.value = it },
                    label = { Text(stringResource(R.string.rentals_search_orders)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(active, { viewModel.activeOnly.value = true }, { Text(stringResource(R.string.rentals_filter_active)) }, Modifier.heightIn(min = MinTouch))
                    FilterChip(!active, { viewModel.activeOnly.value = false }, { Text(stringResource(R.string.rentals_filter_all_orders)) }, Modifier.heightIn(min = MinTouch))
                }
            }
            if (orders.isEmpty()) item { EmptyState(stringResource(R.string.rentals_orders_empty)) }
            items(orders, key = { it.id }) { o ->
                OrderRow(o) { onOpenOrder(o.id) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OrderRow(o: OrderView, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        overlineContent = { Text(stringResource(orderStatusLabel(o.status))) },
        headlineContent = { Text(listOfNotNull(o.number ?: stringResource(R.string.rentals_draft_order), o.customerName).joinToString(" · ")) },
        supportingContent = { Text("${formatDateTime(o.startAt, zone)} → ${formatDateTime(o.endAt, zone)}") },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(o.total), style = MaterialTheme.typography.bodyMedium)
                SyncStateBadge(o.localState)
            }
        },
    )
}

// ── Order detail ─────────────────────────────────────────────────────────────────────

data class LedgerRow(val id: String, val kind: String, val method: String?, val amount: Money?, val at: String?, val localState: String)
data class InvoiceRow(val id: String, val number: String?, val status: String, val total: Money?, val localState: String)
data class HandoverRow(val id: String, val kind: String, val at: String?, val assetId: String?, val localState: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RentalOrderViewModel @Inject constructor(
    private val repo: RentalRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val requestedId: String = requireNotNull(savedState["orderId"])

    val order: StateFlow<OrderView?> = repo.record(RentalEntity.ORDER, requestedId)
        .map { it?.let(OrderView::of) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Children follow the confirmed server id once the draft order has synced. */
    private fun <T> children(entity: String, map: (RentalRecordEntity, String) -> T?): StateFlow<List<T>> =
        order.flatMapLatest { o ->
            if (o == null) flowOf(emptyList()) else repo.byOrder(entity, o.id).map { rows -> rows.mapNotNull { map(it, o.currency) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lines = children(RentalEntity.ORDER_LINE) { r, cur -> LineView.of(r, cur) }
    val ledger = children(RentalEntity.LEDGER) { r, cur ->
        val o = r.obj
        LedgerRow(r.id, o.str("kind") ?: "payment", o.str("method"), Money.of(o.long("amount_minor"), o.str("currency") ?: cur), o.str("received_at"), r.localState)
    }
    val invoices = children(RentalEntity.INVOICE) { r, cur ->
        val o = r.obj
        InvoiceRow(r.id, o.str("number"), o.str("status") ?: "draft", Money.of(o.long("total_minor"), o.str("currency") ?: cur), r.localState)
    }
    val handovers = children(RentalEntity.HANDOVER) { r, _ ->
        val o = r.obj
        HandoverRow(r.id, o.str("kind") ?: "", o.str("at"), o.str("asset_id") ?: r.assetId, r.localState)
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Timber.w(e, "rental order action failed")
            _messages.tryEmit(e.message ?: e.javaClass.simpleName)
        }
    }

    fun action(action: String, reason: String? = null, endAt: Instant? = null) = launchSafe {
        repo.orderAction(order.value?.id ?: requestedId, action, reason, endAt)
    }

    fun recordPayment(input: RentalRepository.PaymentInput) = launchSafe { repo.recordPayment(input) }

    fun createInvoice() = launchSafe { repo.createInvoiceDraft(order.value?.id ?: requestedId) }

    fun syncNow() = repo.requestSync()
}

@Composable
fun RentalOrderScreen(
    onBack: () -> Unit,
    onHandover: (orderId: String, direction: String) -> Unit,
    onOpenCustomer: (String) -> Unit,
    viewModel: RentalOrderViewModel = hiltViewModel(),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val handovers by viewModel.handovers.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPayment by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val o = order
    val zone = zoneOf(null)

    Scaffold(
        topBar = { RentalsTopBar(o?.number ?: stringResource(R.string.rentals_draft_order), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (o == null) {
            EmptyState(stringResource(R.string.rentals_order_missing), Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(orderStatusLabel(o.status)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        SyncStateBadge(o.localState, failed = isSyncFailed(o.notice))
                    }
                    if (o.isDraft) {
                        Text(stringResource(R.string.rentals_draft_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    o.notice?.let { Text(noticeText(it, conflict = true), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                o.customerName?.let { name ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = o.customerId != null, role = Role.Button) { o.customerId?.let(onOpenCustomer) },
                        overlineContent = { Text(stringResource(R.string.rentals_customer)) },
                        headlineContent = { Text(name) },
                    )
                }
                LabelValue(stringResource(R.string.rentals_from), formatDateTime(o.startAt, zone))
                LabelValue(stringResource(R.string.rentals_to), formatDateTime(o.endAt, zone))
                o.holdExpiresAt?.let { LabelValue(stringResource(R.string.rentals_hold_expires), formatDateTime(it, zone)) }
                LabelValue(stringResource(R.string.rentals_total), formatMoney(o.total) + if (o.isDraft) " *" else "")
                LabelValue(stringResource(R.string.rentals_paid), formatMoney(o.paid))
                LabelValue(stringResource(R.string.rentals_balance), formatMoney(o.balance))
                o.depositRequired?.let { LabelValue(stringResource(R.string.rentals_deposit), formatMoney(it)) }
            }
            item { ActionsRow(o, onAction = { a ->
                when (a) {
                    "checkout" -> onHandover(o.id, "out")
                    "checkin" -> onHandover(o.id, "in")
                    "extend" -> showExtend = true
                    "cancel", "no-show" -> confirmAction = a
                    else -> viewModel.action(a)
                }
            }) }
            item { SectionHeader(stringResource(R.string.rentals_lines)) }
            items(lines, key = { "l:${it.id}" }) { l ->
                ListItem(
                    headlineContent = { Text(l.description ?: l.kind) },
                    supportingContent = { Text("${PricingQty(l.qty)} · ${formatDateTime(l.startAt, zone)} → ${formatDateTime(l.endAt, zone)}") },
                    trailingContent = { Text(formatMoney(l.amount)) },
                )
            }
            item {
                SectionHeader(stringResource(R.string.rentals_payments)) {
                    TextButton(onClick = { showPayment = true }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_record_payment)) }
                }
            }
            if (ledger.isEmpty()) item { EmptyState(stringResource(R.string.rentals_payments_empty)) }
            items(ledger, key = { "p:${it.id}" }) { p ->
                ListItem(
                    headlineContent = { Text(stringResource(ledgerKindLabel(p.kind))) },
                    supportingContent = { p.method?.let { Text(stringResource(methodLabel(it))) } },
                    trailingContent = { Column(horizontalAlignment = Alignment.End) { Text(formatMoney(p.amount)); SyncStateBadge(p.localState) } },
                )
            }
            item {
                SectionHeader(stringResource(R.string.rentals_invoices)) {
                    TextButton(onClick = viewModel::createInvoice, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_create_invoice)) }
                }
            }
            items(invoices, key = { "i:${it.id}" }) { inv ->
                ListItem(
                    headlineContent = { Text(inv.number ?: stringResource(R.string.rentals_invoice_draft)) },
                    supportingContent = { Text(inv.status.replace('_', ' ')) },
                    trailingContent = { Column(horizontalAlignment = Alignment.End) { Text(formatMoney(inv.total)); SyncStateBadge(inv.localState) } },
                )
            }
            if (handovers.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.rentals_handovers)) }
                items(handovers, key = { "h:${it.id}" }) { h ->
                    ListItem(
                        headlineContent = { Text(stringResource(if (h.kind == "checkout") R.string.rentals_action_checkout else R.string.rentals_action_checkin)) },
                        supportingContent = { Text(h.at?.let { com.avago.core.data.rental.RentalTime.parseInstant(it) }?.let { formatDateTime(it, zone) } ?: "—") },
                        trailingContent = { SyncStateBadge(h.localState) },
                    )
                }
            }
        }
    }
    if (showPayment && o != null) {
        PaymentSheet(
            orderId = o.id,
            currency = o.currency,
            suggested = o.balance?.takeIf { it.minor > 0 },
            onDismiss = { showPayment = false },
            onSave = { viewModel.recordPayment(it); showPayment = false },
        )
    }
    if (showExtend && o != null) {
        ExtendDialog(o.endAt ?: Instant.now(), zone, onDismiss = { showExtend = false }) { end ->
            viewModel.action("extend", endAt = end); showExtend = false
        }
    }
    confirmAction?.let { a ->
        ReasonDialog(stringResource(actionLabel(a)), onDismiss = { confirmAction = null }) { reason ->
            viewModel.action(a, reason = reason); confirmAction = null
        }
    }
}

private fun PricingQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsRow(o: OrderView, onAction: (String) -> Unit) {
    // `swap` lands with stage B (server answers 501 today) — hidden until then.
    val actions = OrderStatus.actions(o.status).filter { it != "swap" }
    if (actions.isEmpty()) return
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { i, a ->
            if (i == 0) {
                Button(onClick = { onAction(a) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(actionLabel(a))) }
            } else {
                OutlinedButton(onClick = { onAction(a) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(actionLabel(a))) }
            }
        }
    }
}

@Composable
private fun ExtendDialog(current: Instant, zone: ZoneId, onDismiss: () -> Unit, onConfirm: (Instant) -> Unit) {
    var end by remember { mutableStateOf(current.plusSeconds(86_400)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rentals_action_extend)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeField(stringResource(R.string.rentals_new_end), end, zone, { end = it })
                Text(stringResource(R.string.rentals_extend_explainer), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(end) }, enabled = end.isAfter(current)) { Text(stringResource(R.string.rentals_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}

@Composable
private fun ReasonDialog(title: String, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.rentals_reason)) }) },
        confirmButton = { TextButton(onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) }) { Text(stringResource(R.string.rentals_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}

@StringRes
internal fun ledgerKindLabel(kind: String): Int = when (kind) {
    "payment" -> R.string.rentals_kind_payment
    "refund" -> R.string.rentals_kind_refund
    "deposit_received" -> R.string.rentals_kind_deposit_received
    "deposit_refunded" -> R.string.rentals_kind_deposit_refunded
    "deposit_applied" -> R.string.rentals_kind_deposit_applied
    "deposit_forfeited" -> R.string.rentals_kind_deposit_forfeited
    else -> R.string.rentals_kind_other
}

@StringRes
internal fun methodLabel(method: String): Int = when (method) {
    "cash" -> R.string.rentals_method_cash
    "card_external" -> R.string.rentals_method_card_external
    "bank_transfer" -> R.string.rentals_method_bank_transfer
    "cheque" -> R.string.rentals_method_cheque
    "mobile_money" -> R.string.rentals_method_mobile_money
    "direct_debit" -> R.string.rentals_method_direct_debit
    "voucher" -> R.string.rentals_method_voucher
    else -> R.string.rentals_method_other
}

/**
 * Manual payment capture (§5.1): kind, method, amount, reference, external-terminal card brand
 * and last 4 (never a card number), payer. `provider` stays null — gateway-ready, no Stripe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PaymentSheet(
    orderId: String,
    currency: String,
    suggested: Money?,
    onDismiss: () -> Unit,
    onSave: (RentalRepository.PaymentInput) -> Unit,
) {
    var kind by remember { mutableStateOf("payment") }
    var method by remember { mutableStateOf("cash") }
    var amount by remember { mutableStateOf(suggested?.let { MoneyFormatter.toInput(it) } ?: "") }
    var reference by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var payerName by remember { mutableStateOf("") }
    var payerEmail by remember { mutableStateOf("") }
    var authorization by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val parsed = MoneyFormatter.parse(amount, currency)?.takeIf { it.minor > 0 }
    val last4Ok = last4.isEmpty() || last4.matches(Regex("\\d{4}"))
    val card = method == "card_external"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.rentals_record_payment), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RentalRepository.PaymentInput.KINDS.forEach { k ->
                    FilterChip(kind == k, { kind = k }, { Text(stringResource(ledgerKindLabel(k))) }, Modifier.heightIn(min = MinTouch))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RentalRepository.PaymentInput.METHODS.forEach { m ->
                    FilterChip(method == m, { method = m }, { Text(stringResource(methodLabel(m))) }, Modifier.heightIn(min = MinTouch))
                }
            }
            OutlinedTextField(
                amount, { amount = it },
                label = { Text(stringResource(R.string.rentals_amount_in, currency)) },
                isError = amount.isNotEmpty() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(reference, { reference = it }, label = { Text(stringResource(R.string.rentals_reference)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (card) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.rentals_card_brand)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        last4, { v -> last4 = v.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.rentals_card_last4)) },
                        isError = !last4Ok,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_authorization_hold), modifier = Modifier.weight(1f))
                    Switch(authorization, { authorization = it })
                }
            }
            OutlinedTextField(payerName, { payerName = it }, label = { Text(stringResource(R.string.rentals_payer_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                payerEmail, { payerEmail = it },
                label = { Text(stringResource(R.string.rentals_payer_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.rentals_notes)) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.rentals_payment_manual_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = parsed != null && last4Ok,
                onClick = {
                    onSave(
                        RentalRepository.PaymentInput(
                            orderId = orderId,
                            kind = kind,
                            method = method,
                            amount = parsed!!,
                            reference = reference,
                            cardBrand = brand.takeIf { card },
                            last4 = last4.takeIf { card && it.length == 4 },
                            payerName = payerName,
                            payerEmail = payerEmail,
                            isAuthorization = card && authorization,
                            notes = notes,
                            receivedAt = Instant.now(),
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .padding(bottom = 24.dp),
            ) { Text(stringResource(R.string.rentals_save)) }
        }
    }
}
