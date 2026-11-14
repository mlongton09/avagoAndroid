package com.avago.feature.rentals.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.data.rental.RentalTime
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.SkeletonListItem
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.StickyDayHeader
import com.avago.core.ui.components.TimelineEntry
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.invoiceStatusText
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
import kotlinx.coroutines.delay
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
class RentalOrdersViewModel @Inject constructor(private val repo: RentalRepository) : ViewModel() {
    val activeOnly = MutableStateFlow(true)
    val query = MutableStateFlow("")
    val orders: StateFlow<List<OrderView>> = combine(repo.records(RentalEntity.ORDER), activeOnly, query) { rows, active, q ->
        rows.map(OrderView::of)
            .filter { !active || it.status !in setOf(OrderStatus.CLOSED, OrderStatus.CANCELLED) }
            .filter { q.isBlank() || it.number?.contains(q, true) == true || it.customerName?.contains(q, true) == true }
            .sortedWith(compareByDescending<OrderView> { it.startAt ?: Instant.MIN })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** For pull-to-refresh: the spinner ends when the background sync stamps a new time. */
    val lastSyncedAt: StateFlow<Long?> = repo.lastSyncedAt().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun syncNow() = repo.requestSync()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val refresh = rememberSyncRefresh(lastSynced, viewModel::syncNow)
    val firstLoad = rememberFirstLoad(orders.isEmpty() && query.isBlank(), lastSynced)
    val listState = rememberLazyListState()
    val zone = ZoneId.systemDefault()
    // Sticky day headers: orders are sorted by start (newest first) so groups stay contiguous.
    val groups = remember(orders) { orders.groupBy { o -> o.startAt?.atZone(zone)?.toLocalDate() } }
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_orders), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickBook,
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.rentals_quick_book)) },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refresh.refreshing,
            onRefresh = refresh.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 96.dp)) {
                item(key = "search", contentType = "search") {
                    RentalSearchField(query, { viewModel.query.value = it }, stringResource(R.string.rentals_search_orders))
                }
                item(key = "filters", contentType = "filters") {
                    ChipRow {
                        FilterChip(
                            active, { viewModel.activeOnly.value = true }, { Text(stringResource(R.string.rentals_filter_active)) },
                            Modifier.heightIn(min = MinTouch),
                            leadingIcon = { Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)) },
                        )
                        FilterChip(
                            !active, { viewModel.activeOnly.value = false }, { Text(stringResource(R.string.rentals_filter_all_orders)) },
                            Modifier.heightIn(min = MinTouch),
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(16.dp)) },
                        )
                    }
                }
                when {
                    firstLoad -> items(6, key = { "sk$it" }, contentType = { "skeleton" }) { SkeletonListItem() }
                    orders.isEmpty() && query.isNotBlank() -> item(key = "nomatch", contentType = "empty") {
                        EmptyState(
                            stringResource(R.string.rentals_no_matches),
                            icon = Icons.Default.SearchOff,
                            actionLabel = stringResource(R.string.rentals_clear_search),
                            onAction = { viewModel.query.value = "" },
                        )
                    }
                    orders.isEmpty() -> item(key = "empty", contentType = "empty") {
                        EmptyState(
                            stringResource(R.string.rentals_orders_empty),
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            actionLabel = stringResource(R.string.rentals_quick_book),
                            onAction = onQuickBook,
                        )
                    }
                }
                groups.forEach { (day, rows) ->
                    stickyHeader(key = "h:${day ?: "none"}", contentType = "header") {
                        StickyDayHeader(day?.let { dayLabel(it, zone) } ?: stringResource(R.string.rentals_no_date), count = rows.size)
                    }
                    items(rows, key = { it.id }, contentType = { "order" }) { o ->
                        OrderRow(o, zone, Modifier.animateItem()) { onOpenOrder(o.id) }
                    }
                }
            }
        }
    }
}

/** Order card row: status badge, customer + number, period, trailing total and pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderRow(o: OrderView, zone: ZoneId, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LeadingBadge(orderStatusIcon(o.status), orderStatusTone(o.status))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            o.customerName ?: o.number ?: stringResource(R.string.rentals_draft_order),
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            o.number ?: stringResource(R.string.rentals_draft_order),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    Text(formatMoney(o.total), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
                }
                Text(
                    "${formatDateTime(o.startAt, zone)} → ${formatDateTime(o.endAt, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OrderStatusPill(o.status)
                    SyncStateBadge(o.localState, failed = isSyncFailed(o.notice))
                    o.balance?.takeIf { it.minor > 0 && !o.isDraft }?.let {
                        StatusPill(stringResource(R.string.rentals_balance_due, formatMoney(it)), StatusTone.Warning, icon = Icons.Default.Payments)
                    }
                }
            }
        }
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RentalOrderScreen(
    onBack: () -> Unit,
    onHandover: (orderId: String, direction: String) -> Unit,
    onOpenCustomer: (String) -> Unit,
    onSwap: ((orderId: String) -> Unit)? = null,
    viewModel: RentalOrderViewModel = hiltViewModel(),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val handovers by viewModel.handovers.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var showPayment by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var missingTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            haptics.error()
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(Unit) {
        delay(1_500)
        missingTimedOut = true
    }
    val paymentRecorded = stringResource(R.string.rentals_payment_recorded)
    val scope = rememberCoroutineScope()
    val o = order
    val zone = zoneOf(null)

    Scaffold(
        topBar = { RentalsTopBar(o?.number ?: stringResource(R.string.rentals_draft_order), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (o != null) {
                OrderActionBar(
                    o, canSwap = onSwap != null,
                    onCollect = { showPayment = true },
                    onAction = { a ->
                        when (a) {
                            "checkout" -> onHandover(o.id, "out")
                            "checkin" -> onHandover(o.id, "in")
                            "swap" -> onSwap?.invoke(o.id)
                            "extend" -> showExtend = true
                            "cancel", "no-show" -> confirmAction = a
                            else -> viewModel.action(a)
                        }
                    },
                )
            }
        },
    ) { pad ->
        if (o == null) {
            // The record arrives from Room almost instantly; only claim "missing" after a short grace.
            if (missingTimedOut) {
                EmptyState(stringResource(R.string.rentals_order_missing), Modifier.padding(pad), icon = Icons.Default.SearchOff)
            } else {
                SkeletonList(Modifier.padding(pad), rows = 4)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "summary", contentType = "summary") { OrderSummaryCard(o, zone, onOpenCustomer) }
            item(key = "lines", contentType = "section") {
                SectionCard(title = stringResource(R.string.rentals_lines)) {
                    if (lines.isEmpty()) CardNote(stringResource(R.string.rentals_handover_no_unit))
                    lines.forEachIndexed { i, l ->
                        if (i > 0) CardDivider()
                        val statusText = l.status?.let { lineStatusLabel(it) }?.let { stringResource(it) }
                        ListItem(
                            colors = cardListItemColors(),
                            leadingContent = { LeadingBadge(Icons.Default.Inventory2, if (l.status == "out") StatusTone.Accent else StatusTone.Neutral) },
                            overlineContent = statusText?.let { { Text(it) } },
                            headlineContent = { Text(l.description ?: l.kind) },
                            supportingContent = { Text("${PricingQty(l.qty)} × · ${formatDateTime(l.startAt, zone)} → ${formatDateTime(l.endAt, zone)}") },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatMoney(l.amount), style = MaterialTheme.typography.bodyMedium)
                                    SyncStateBadge(l.localState)
                                }
                            },
                        )
                    }
                }
            }
            item(key = "payments", contentType = "section") {
                SectionCard(
                    title = stringResource(R.string.rentals_payments),
                    trailing = {
                        TextButton(onClick = { showPayment = true }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.rentals_record_payment), Modifier.padding(start = 4.dp))
                        }
                    },
                ) {
                    if (ledger.isEmpty()) CardNote(stringResource(R.string.rentals_payments_empty))
                    ledger.forEachIndexed { i, p ->
                        if (i > 0) CardDivider()
                        val outgoing = p.kind == "refund" || p.kind == "deposit_refunded"
                        val at = p.at?.let { RentalTime.parseInstant(it) }?.let { formatDateTime(it, zone) }
                        val method = p.method?.let { stringResource(methodLabel(it)) }
                        ListItem(
                            colors = cardListItemColors(),
                            leadingContent = {
                                LeadingBadge(
                                    if (outgoing) Icons.AutoMirrored.Filled.Undo else Icons.Default.Payments,
                                    if (outgoing) StatusTone.Warning else StatusTone.Success,
                                )
                            },
                            headlineContent = { Text(stringResource(ledgerKindLabel(p.kind))) },
                            supportingContent = { Text(listOfNotNull(method, at).joinToString(" · ")) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatMoney(p.amount), style = MaterialTheme.typography.bodyMedium)
                                    SyncStateBadge(p.localState)
                                }
                            },
                        )
                    }
                }
            }
            item(key = "invoices", contentType = "section") {
                SectionCard(
                    title = stringResource(R.string.rentals_invoices),
                    trailing = {
                        TextButton(onClick = viewModel::createInvoice, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.rentals_create_invoice))
                        }
                    },
                ) {
                    if (invoices.isEmpty()) CardNote(stringResource(R.string.rentals_invoices_none))
                    invoices.forEachIndexed { i, inv ->
                        if (i > 0) CardDivider()
                        ListItem(
                            colors = cardListItemColors(),
                            leadingContent = { LeadingBadge(Icons.Default.Description, StatusTone.Info) },
                            headlineContent = { Text(inv.number ?: stringResource(R.string.rentals_invoice_draft)) },
                            supportingContent = { Text(invoiceStatusText(inv.status)) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatMoney(inv.total), style = MaterialTheme.typography.bodyMedium)
                                    SyncStateBadge(inv.localState)
                                }
                            },
                        )
                    }
                }
            }
            if (handovers.isNotEmpty()) {
                item(key = "history", contentType = "section") {
                    SectionCard(title = stringResource(R.string.rentals_order_history), contentPadding = true) {
                        val sorted = handovers.sortedByDescending { h -> h.at?.let { RentalTime.parseInstant(it) } ?: Instant.MIN }
                        sorted.forEachIndexed { i, h ->
                            val out = h.kind == "checkout"
                            TimelineEntry(
                                title = stringResource(if (out) R.string.rentals_action_checkout else R.string.rentals_action_checkin),
                                icon = if (out) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                                tone = if (out) StatusTone.Accent else StatusTone.Success,
                                time = h.at?.let { RentalTime.parseInstant(it) }?.let { formatDateTime(it, zone) },
                                trailing = if (h.localState != RentalRecordEntity.STATE_SYNCED) stringResource(R.string.rentals_sync_pending) else null,
                                isFirst = i == 0,
                                isLast = i == sorted.lastIndex,
                            )
                        }
                    }
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
            onSave = {
                viewModel.recordPayment(it)
                showPayment = false
                haptics.success()
                scope.launch { snackbar.showSnackbar(paymentRecorded) }
            },
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

/** Muted one-line note inside a [SectionCard] (section has no rows yet). */
@Composable
internal fun CardNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/** Header summary: status + sync, customer, period, money with the balance emphasised. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderSummaryCard(o: OrderView, zone: ZoneId, onOpenCustomer: (String) -> Unit) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OrderStatusPill(o.status)
                SyncStateBadge(o.localState, failed = isSyncFailed(o.notice))
            }
            if (o.isDraft) {
                Text(
                    stringResource(R.string.rentals_draft_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            o.notice?.let {
                ErrorBanner(
                    noticeText(it, conflict = true),
                    modifier = Modifier.fillMaxWidth(),
                    tone = if (isSyncFailed(it)) StatusTone.Danger else StatusTone.Warning,
                )
            }
        }
        o.customerName?.let { name ->
            CardDivider()
            ListItem(
                colors = cardListItemColors(),
                modifier = Modifier.clickable(enabled = o.customerId != null, role = Role.Button) { o.customerId?.let(onOpenCustomer) },
                leadingContent = { LeadingBadge(Icons.Default.Person, StatusTone.Neutral) },
                overlineContent = { Text(stringResource(R.string.rentals_customer)) },
                headlineContent = { Text(name) },
                trailingContent = if (o.customerId != null) {
                    { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                } else {
                    null
                },
            )
        }
        CardDivider()
        Column(Modifier.padding(vertical = 8.dp)) {
            LabelValue(stringResource(R.string.rentals_from), formatDateTime(o.startAt, zone))
            LabelValue(stringResource(R.string.rentals_to), formatDateTime(o.endAt, zone))
            o.holdExpiresAt?.let { LabelValue(stringResource(R.string.rentals_hold_expires), formatDateTime(it, zone)) }
        }
        CardDivider()
        Column(Modifier.padding(vertical = 8.dp)) {
            LabelValue(stringResource(R.string.rentals_total), formatMoney(o.total) + if (o.isDraft) " *" else "")
            LabelValue(stringResource(R.string.rentals_paid), formatMoney(o.paid))
            o.depositRequired?.let { LabelValue(stringResource(R.string.rentals_deposit), formatMoney(it)) }
            LabelValue(stringResource(R.string.rentals_balance), formatMoney(o.balance), emphasise = true)
        }
    }
}

private fun PricingQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

/**
 * Sticky bottom bar with the next step: the first lifecycle action as the primary button,
 * "Collect payment" when a balance is open, and the remaining actions in an overflow menu.
 */
@Composable
private fun OrderActionBar(o: OrderView, canSwap: Boolean, onCollect: () -> Unit, onAction: (String) -> Unit) {
    // `swap` is line level (stage B): shown when the caller can open the swap screen.
    val actions = OrderStatus.actions(o.status).filter { it != "swap" || canSwap }
    val owes = !o.isDraft && (o.balance?.minor ?: 0) > 0
    if (actions.isEmpty() && !owes) return
    var menu by remember { mutableStateOf(false) }
    val primary = actions.firstOrNull()
    val rest = actions.drop(1)
    BottomActionBar {
        if (rest.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.rentals_more_actions))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    rest.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(stringResource(actionLabel(a))) },
                            onClick = {
                                menu = false
                                onAction(a)
                            },
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                    }
                }
            }
        }
        if (owes) {
            val label = stringResource(R.string.rentals_collect_payment)
            if (primary == null) {
                Button(onClick = onCollect, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                    Text(label, Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
                }
            } else {
                OutlinedButton(onClick = onCollect, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                    Text(label, Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
                }
            }
        }
        primary?.let { a ->
            Button(onClick = { onAction(a) }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                Text(stringResource(actionLabel(a)), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ExtendDialog(current: Instant, zone: ZoneId, onDismiss: () -> Unit, onConfirm: (Instant) -> Unit) {
    var end by remember { mutableStateOf(current.plusSeconds(86_400)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EditCalendar, null) },
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
        text = {
            OutlinedTextField(
                reason, { reason = it },
                label = { Text(stringResource(R.string.rentals_reason)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) }) { Text(stringResource(R.string.rentals_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rentals_cancel)) } },
    )
}

@StringRes
internal fun lineStatusLabel(status: String): Int? = when (status) {
    "open" -> R.string.rentals_line_status_open
    "out" -> R.string.rentals_line_status_out
    "returned" -> R.string.rentals_line_status_returned
    "swapped" -> R.string.rentals_line_status_swapped
    "cancelled" -> R.string.rentals_line_status_cancelled
    else -> null
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
    var attempted by remember { mutableStateOf(false) }
    val parsed = MoneyFormatter.parse(amount, currency)?.takeIf { it.minor > 0 }
    val last4Ok = last4.isEmpty() || last4.matches(Regex("\\d{4}"))
    val card = method == "card_external"
    val amountError = (attempted || amount.isNotEmpty()) && parsed == null
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    val save: () -> Unit = {
        attempted = true
        if (parsed != null && last4Ok) {
            onSave(
                RentalRepository.PaymentInput(
                    orderId = orderId,
                    kind = kind,
                    method = method,
                    amount = parsed,
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
        } else {
            haptics.error()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.rentals_record_payment),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                amount, { amount = it },
                label = { Text(stringResource(R.string.rentals_amount_in, currency)) },
                prefix = { Text("$currency ") },
                isError = amountError,
                supportingText = when {
                    amountError -> { { Text(stringResource(R.string.rentals_amount_invalid)) } }
                    suggested != null -> { { Text(stringResource(R.string.rentals_balance_due, formatMoney(suggested))) } }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = next,
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldLabel(stringResource(R.string.rentals_payment_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RentalRepository.PaymentInput.KINDS.forEach { k ->
                    FilterChip(kind == k, { kind = k }, { Text(stringResource(ledgerKindLabel(k))) }, Modifier.heightIn(min = MinTouch))
                }
            }
            FieldLabel(stringResource(R.string.rentals_payment_method))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RentalRepository.PaymentInput.METHODS.forEach { m ->
                    FilterChip(method == m, { method = m }, { Text(stringResource(methodLabel(m))) }, Modifier.heightIn(min = MinTouch))
                }
            }
            OutlinedTextField(
                reference, { reference = it },
                label = { Text(stringResource(R.string.rentals_reference)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = next,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (card) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        brand, { brand = it },
                        label = { Text(stringResource(R.string.rentals_card_brand)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = next,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        last4, { v -> last4 = v.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.rentals_card_last4)) },
                        isError = !last4Ok,
                        supportingText = if (!last4Ok) {
                            { Text(stringResource(R.string.rentals_last4_invalid)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                        keyboardActions = next,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouch)
                        .toggleable(value = authorization, role = Role.Switch, onValueChange = { authorization = it }),
                ) {
                    Text(stringResource(R.string.rentals_authorization_hold), modifier = Modifier.weight(1f))
                    Switch(authorization, null)
                }
            }
            OutlinedTextField(
                payerName, { payerName = it },
                label = { Text(stringResource(R.string.rentals_payer_name)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = next,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                payerEmail, { payerEmail = it },
                label = { Text(stringResource(R.string.rentals_payer_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = next,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                notes, { notes = it },
                label = { Text(stringResource(R.string.rentals_notes)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.rentals_payment_manual_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = save, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Text(
                    parsed?.let { stringResource(R.string.rentals_save_amount, formatMoney(it)) } ?: stringResource(R.string.rentals_save),
                    Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

/** Small group label above chips / fields in forms and sheets. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}
