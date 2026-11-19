package com.avago.feature.rentals.ui

import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.avago.core.auth.IdentityManager
import com.avago.core.data.domain.PaymentDraft
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalHandover
import com.avago.core.data.domain.RentalInvoice
import com.avago.core.data.domain.RentalOrderAction
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.SyncRepository
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.permissions.PermissionsManager
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
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
import com.avago.core.ui.components.toneColors
import com.avago.core.ui.invoiceStatusText
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.FeeBreakdown
import com.avago.feature.rentals.domain.HandoverProgress
import com.avago.feature.rentals.domain.LineView
import com.avago.feature.rentals.domain.OnlinePayments
import com.avago.feature.rentals.domain.OrderFilter
import com.avago.feature.rentals.domain.OrderStatus
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.OrderWorklist
import com.avago.feature.rentals.domain.OrderWorklistState
import com.avago.feature.rentals.domain.observeOrderWorklist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

// â”€â”€ Orders list â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@HiltViewModel
class RentalOrdersViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val sync: SyncRepository,
    private val permissions: PermissionsManager,
    identity: IdentityManager,
) : ViewModel() {
    /** Gates the "Record payment" swipe (server enforces it too). */
    val canRecordPayment = permissions.observeCan(PERMISSION_PAYMENT_RECORD)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val canRefund = permissions.observeCan(PERMISSION_REFUND)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _paymentResults = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val paymentResults: SharedFlow<String?> = _paymentResults

    fun recordPayment(draft: PaymentDraft) = viewModelScope.launch {
        try {
            require(permissions.can(PERMISSION_PAYMENT_RECORD)) { "Missing rental permission" }
            require(draft.kind !in REFUND_KINDS || permissions.can(PERMISSION_REFUND)) { "Missing rental refund permission" }
            repo.recordPayment(draft)
            _paymentResults.emit(null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "record payment from list failed")
            _paymentResults.emit(e.message ?: e.javaClass.simpleName)
        }
    }

    val filter = MutableStateFlow(OrderFilter.ACTIVE)
    val query = MutableStateFlow("")
    val worklist: StateFlow<OrderWorklistState> = observeOrderWorklist(
        identity.activeAccountId,
        repo.observeOrders().map { rows -> rows.map(OrderView::of) },
        filter,
        query,
        flow {
            while (true) {
                emit(Instant.now())
                delay(30_000)
            }
        },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), OrderWorklistState())

    suspend fun syncNow(): Boolean = sync.syncNow()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RentalOrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onQuickBook: () -> Unit,
    viewModel: RentalOrdersViewModel = hiltViewModel(),
) {
    val worklist by viewModel.worklist.collectAsStateWithLifecycle()
    val canRecordPayment by viewModel.canRecordPayment.collectAsStateWithLifecycle()
    val canRefund by viewModel.canRefund.collectAsStateWithLifecycle()
    val orders = worklist.orders
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val refresh = rememberSyncRefresh(viewModel::syncNow)
    val firstLoad = rememberFirstLoad(orders.isEmpty() && query.isBlank())
    val listState = rememberLazyListState()
    val zone = ZoneId.systemDefault()
    // Sticky day headers: orders are sorted by start (newest first) so groups stay contiguous.
    val groups = remember(orders, filter) {
        orders.groupBy { o -> (if (filter == OrderFilter.OVERDUE) o.endAt else o.startAt)?.atZone(zone)?.toLocalDate() }
    }
    var payFor by remember(worklist.accountId) { mutableStateOf<OrderView?>(null) }
    var statusMenu by remember { mutableStateOf(false) }
    val balances = remember(orders) { OrderWorklist.balances(orders) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val recordPaymentLabel = stringResource(R.string.rentals_record_payment)
    val paymentRecorded = stringResource(R.string.rentals_payment_recorded)
    LaunchedEffect(viewModel, paymentRecorded) {
        viewModel.paymentResults.collect { error ->
            if (error == null) haptics.success() else haptics.error()
            snackbar.showSnackbar(error ?: paymentRecorded)
        }
    }
    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_orders), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
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
            LazyColumn(
                Modifier.fillMaxSize().readableWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + AvagoSpacing.fabClearance),
            ) {
                if (refresh.failed) {
                    item(key = "refresh-failed", contentType = "banner") { RefreshFailedBanner(refresh) }
                }
                item(key = "search", contentType = "search") {
                    RentalSearchField(query, { viewModel.query.value = it }, stringResource(R.string.rentals_search_orders))
                }
                item(key = "filters", contentType = "filters") {
                    ChipRow {
                        listOf(
                            OrderFilter.ACTIVE to R.string.rentals_filter_active,
                            OrderFilter.ALL to R.string.rentals_filter_all_orders,
                            OrderFilter.OVERDUE to R.string.rentals_worklist_overdue,
                            OrderFilter.BALANCE_DUE to R.string.rentals_worklist_balance_due,
                        ).forEach { (value, label) ->
                            FilterChip(
                                filter == value, { viewModel.filter.value = value },
                                { Text(stringResource(label)) }, Modifier.heightIn(min = MinTouch),
                            )
                        }
                        Box {
                            FilterChip(
                                filter.status != null, { statusMenu = true },
                                { Text(stringResource(filter.status?.let(::orderStatusLabel) ?: R.string.rentals_worklist_status)) },
                                Modifier.heightIn(min = MinTouch),
                            )
                            DropdownMenu(statusMenu, { statusMenu = false }) {
                                OrderFilter.entries.filter { it.status != null }.forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(orderStatusLabel(requireNotNull(value.status)))) },
                                        onClick = { viewModel.filter.value = value; statusMenu = false },
                                    )
                                }
                            }
                        }
                    }
                }
                if (balances.isNotEmpty()) {
                    item(key = "balances", contentType = "summary") {
                        SectionCard(
                            title = stringResource(R.string.rentals_worklist_balances),
                            modifier = Modifier.padding(horizontal = AvagoSpacing.cardHorizontal, vertical = AvagoSpacing.cardVertical),
                        ) {
                            Text(
                                balances.joinToString(" Â· ") { "${it.currency} ${formatMoney(it)}" },
                                modifier = Modifier.padding(AvagoSpacing.md),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.rentals_worklist_snapshot),
                                modifier = Modifier.padding(horizontal = AvagoSpacing.md, vertical = AvagoSpacing.sm),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
                    orders.isEmpty() && filter != OrderFilter.ACTIVE && filter != OrderFilter.ALL ->
                        item(key = "filtered-empty", contentType = "empty") {
                            EmptyState(
                                stringResource(R.string.rentals_worklist_empty),
                                icon = Icons.Default.SearchOff,
                                actionLabel = stringResource(R.string.rentals_filter_all_orders),
                                onAction = { viewModel.filter.value = OrderFilter.ALL },
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
                        val owes = OrderWorklist.canRecordPayment(o) && (o.balance?.minor ?: 0L) > 0
                        val pay = if (canRecordPayment && owes) {
                            SwipeAction(recordPaymentLabel, Icons.Default.Payments, StatusTone.Success) { payFor = o }
                        } else null
                        SwipeActionsRow(
                            start = pay,
                            end = null,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.animateItem().padding(horizontal = AvagoSpacing.cardHorizontal, vertical = AvagoSpacing.cardVertical),
                        ) { a11y ->
                            OrderRow(o, zone, a11y) { onOpenOrder(o.id) }
                        }
                    }
                }
            }
        }
    }
    payFor?.let { o ->
        PaymentSheet(
            orderId = o.id,
            currency = o.currency,
            canRefund = canRefund,
            suggested = o.balance?.takeIf { it.minor > 0 },
            onDismiss = { payFor = null },
            onSave = {
                viewModel.recordPayment(it)
                payFor = null
            },
        )
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
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(AvagoSpacing.md), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
            LeadingBadge(orderStatusIcon(o.status), orderStatusTone(o.status))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
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
                    "${formatDateTime(o.startAt, zone)} â†’ ${formatDateTime(o.endAt, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OrderStatusPill(o.status)
                    SyncStateBadge(o.sync)
                    o.balance?.takeIf { it.minor > 0 && OrderWorklist.canRecordPayment(o) }?.let {
                        StatusPill(stringResource(R.string.rentals_balance_due, formatMoney(it)), StatusTone.Warning, icon = Icons.Default.Payments)
                    }
                }
            }
        }
    }
}

// â”€â”€ Order detail â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RentalOrderViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val api: RentalsApi,
    private val identity: IdentityManager,
    private val permissions: PermissionsManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val orderId: String = requireNotNull(savedState["orderId"])

    val order: StateFlow<OrderView?> = repo.observeOrder(orderId)
        .map { it?.let(OrderView::of) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val canRecordPayment = permissions.observeCan(PERMISSION_PAYMENT_RECORD)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val canRefund = permissions.observeCan(PERMISSION_REFUND)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val canInvoice = permissions.observeCan(PERMISSION_INVOICE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lines: StateFlow<List<LineView>> = combine(order, repo.observeLines(orderId)) { o, ls ->
        if (o == null) emptyList() else ls.map { LineView.of(it, o.currency) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allowedActions = combine(order, lines, permissions.permissions, permissions.isRoot) { o, ls, _, _ ->
        HandoverProgress.actions(o, ls).filter { permissions.can(OrderStatus.permission(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val invoices: StateFlow<List<RentalInvoice>> = repo.observeInvoices(orderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val handovers: StateFlow<List<RentalHandover>> = repo.observeHandovers(orderId = orderId)
        .map { hs -> hs.filter { !it.voided && it.kind != "readiness" }.sortedByDescending { it.at } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Online-only platform fee / payout breakdown (GET); null offline, for unsynced orders or without access. */
    val fees: StateFlow<FeeBreakdown?> = order
        .map { o -> o?.takeIf { it.sync == RecordSyncState.SAVED }?.id }
        .distinctUntilChanged()
        .mapLatest { id -> id?.let { loadFees(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun loadFees(orderId: String): FeeBreakdown? {
        val acc = identity.getActiveAccountId() ?: return null
        return try {
            FeeBreakdown.from(api.feeBreakdown(acc, orderId))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "fee breakdown unavailable")
            null
        }
    }
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages
    private val _paymentQueued = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val paymentQueued: SharedFlow<Unit> = _paymentQueued

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Timber.w(e, "rental order action failed")
            _messages.tryEmit(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Lifecycle step of the order ([OrderStatus.actions]); hand-overs and swaps have their own screens. */
    fun action(action: String, reason: String? = null, endAt: Instant? = null) = launchSafe {
        require(permissions.can(OrderStatus.permission(action))) { "Missing rental permission" }
        repo.act(orderId, orderAction(action, reason, endAt))
    }

    fun recordPayment(draft: PaymentDraft) = launchSafe {
        require(permissions.can(PERMISSION_PAYMENT_RECORD)) { "Missing rental permission" }
        require(draft.kind !in REFUND_KINDS || permissions.can(PERMISSION_REFUND)) { "Missing rental refund permission" }
        repo.recordPayment(draft)
        _paymentQueued.emit(Unit)
    }

    fun createInvoice() = launchSafe {
        require(permissions.can(PERMISSION_INVOICE)) { "Missing rental permission" }
        repo.createInvoiceDraft(orderId)
    }
}

/** Maps a lifecycle action of [OrderStatus.actions] to its catalog command. */
internal fun orderAction(action: String, reason: String?, endAt: Instant?): RentalOrderAction = when (action) {
    "quote" -> RentalOrderAction.Quote
    "hold" -> RentalOrderAction.Hold(reason)
    "reserve" -> RentalOrderAction.Reserve()
    "cancel" -> RentalOrderAction.Cancel(requireNotNull(reason?.takeIf { it.isNotBlank() }) { "A reason is required" })
    "no-show" -> RentalOrderAction.NoShow(reason)
    "extend" -> RentalOrderAction.Extend(requireNotNull(endAt) { "A new end is required" }, reason)
    "off-rent" -> RentalOrderAction.OffRent(Instant.now(), reason = reason)
    "close" -> RentalOrderAction.Close
    else -> error("Not an order lifecycle action: $action")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RentalOrderScreen(
    onBack: () -> Unit,
    onHandover: (orderId: String, direction: String) -> Unit,
    onOpenCustomer: (String) -> Unit,
    onSwap: ((orderId: String) -> Unit)? = null,
    onSign: ((orderId: String) -> Unit)? = null,
    onAddDocument: ((customerId: String) -> Unit)? = null,
    viewModel: RentalOrderViewModel = hiltViewModel(),
    wave2: OrderWave2ViewModel = hiltViewModel(),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val allowedActions by viewModel.allowedActions.collectAsStateWithLifecycle()
    val canRecordPayment by viewModel.canRecordPayment.collectAsStateWithLifecycle()
    val canRefund by viewModel.canRefund.collectAsStateWithLifecycle()
    val canInvoice by viewModel.canInvoice.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val handovers by viewModel.handovers.collectAsStateWithLifecycle()
    val gate by wave2.gate.collectAsStateWithLifecycle()
    val esign by wave2.esign.collectAsStateWithLifecycle()
    val credit by wave2.credit.collectAsStateWithLifecycle()
    val payments by wave2.payments.collectAsStateWithLifecycle()
    val paymentUnavailable by wave2.paymentUnavailable.collectAsStateWithLifecycle()
    val bulkReturns by wave2.bulkReturns.collectAsStateWithLifecycle()
    val wave2Busy by wave2.busy.collectAsStateWithLifecycle()
    var bulkReturn by remember { mutableStateOf<Pair<LineView, java.math.BigDecimal>?>(null) }
    val context = LocalContext.current
    val fees by viewModel.fees.collectAsStateWithLifecycle()
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
    val w2Texts = mapOf(
        OrderWave2ViewModel.Msg.FIX_INCOMPLETE to stringResource(R.string.rentals_gate_fix_incomplete),
        OrderWave2ViewModel.Msg.OFFLINE to stringResource(R.string.rentals_online_offline),
        OrderWave2ViewModel.Msg.BULK_SAVED to stringResource(R.string.rentals_bulk_queued),
        OrderWave2ViewModel.Msg.FAILED to stringResource(R.string.rentals_online_failed_generic),
    )
    val failedWith = stringResource(R.string.rentals_online_failed, "%s")
    val shareTitle = stringResource(R.string.rentals_pay_share)
    LaunchedEffect(Unit) {
        wave2.events.collect { e ->
            when (e) {
                is OrderWave2ViewModel.Event.Message -> {
                    if (e.msg == OrderWave2ViewModel.Msg.BULK_SAVED) haptics.success() else haptics.error()
                    val text = if (e.msg == OrderWave2ViewModel.Msg.FAILED && !e.detail.isNullOrBlank()) {
                        failedWith.replace("%s", e.detail)
                    } else {
                        w2Texts.getValue(e.msg)
                    }
                    snackbar.showSnackbar(text)
                }
                is OrderWave2ViewModel.Event.Share -> runCatching { sharePayLink(context, e.url, shareTitle) }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(1_500)
        missingTimedOut = true
    }
    val paymentRecorded = stringResource(R.string.rentals_payment_recorded)
    LaunchedEffect(viewModel, paymentRecorded) {
        viewModel.paymentQueued.collect {
            haptics.success()
            snackbar.showSnackbar(paymentRecorded)
        }
    }
    val o = order
    val zone = zoneOf(null)

    val onOrderAction: (String) -> Unit = { a ->
        if (o != null && !o.isLegacy) {
            when (a) {
                "checkout" -> onHandover(o.id, "out")
                "checkin" -> onHandover(o.id, "in")
                "swap" -> onSwap?.invoke(o.id)
                "extend" -> showExtend = true
                "cancel", "no-show" -> confirmAction = a
                else -> viewModel.action(a)
            }
        }
    }

    Scaffold(
        topBar = {
            RentalsTopBar(o?.number ?: stringResource(R.string.rentals_draft_order), onBack) {
                if (o != null && !o.isLegacy) {
                    OverflowMenu(
                        orderOverflow(
                            o, canSwap = onSwap != null, onAction = onOrderAction,
                            onPayment = { showPayment = true }, onInvoice = viewModel::createInvoice,
                            allowedActions = allowedActions, canRecordPayment = canRecordPayment, canInvoice = canInvoice,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (o != null) OrderActionBar(
                o, onCollect = { showPayment = true }, onAction = onOrderAction,
                allowedActions = allowedActions, canRecordPayment = canRecordPayment,
            )
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
            Modifier.fillMaxSize().readableWidth(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + AvagoSpacing.lg),
        ) {
            item(key = "summary", contentType = "summary") { OrderSummaryCard(o, zone, onOpenCustomer) }
            if (o.isLegacy) {
                item(key = "legacy", contentType = "section") {
                    SectionCard(title = stringResource(R.string.rentals_worklist_legacy_title), contentPadding = true) {
                        Text(stringResource(R.string.rentals_worklist_legacy))
                    }
                }
            }
            gate?.takeIf { !o.isLegacy }?.let { g ->
                item(key = "gate", contentType = "gate") {
                    OrderGateCard(
                        gate = g.gate,
                        canOverride = wave2.can(g.gate.permission),
                        busy = wave2Busy,
                        onSign = onSign?.let { s -> { s(o.id) } },
                        onAddDocument = o.customerId?.let { c -> onAddDocument?.let { a -> { a(c) } } },
                        onFix = { reason, meter -> wave2.fixGate(g, reason, meter) },
                        onRetry = { wave2.retryGate(g) },
                        onDiscard = { wave2.discardGate(g) },
                    )
                }
            }
            if (!o.isLegacy) {
                item(key = "agreement", contentType = "section") {
                    OrderAgreementCard(esign, onSign?.let { s -> { s(o.id) } })
                }
                item(key = "credit", contentType = "section") {
                    OrderCreditCard(credit, o.customerId?.let { c -> onAddDocument?.let { a -> { a(c) } } })
                }
            }
            item(key = "lines", contentType = "section") {
                OrderLinesCard(
                    lines = lines,
                    zone = zone,
                    bulkReturns = bulkReturns,
                    onBulkReturn = if (o.isLegacy) null else { l, out -> bulkReturn = l to out },
                )
            }
            payments?.takeIf { !o.isLegacy }?.let { pay ->
                item(key = "pay_links", contentType = "section") {
                    OnlinePaymentsCard(
                        payments = pay,
                        zone = zone,
                        onShare = { url -> runCatching { sharePayLink(context, url, shareTitle) } },
                    )
                }
            }
            paymentUnavailable?.takeIf { !o.isLegacy }?.let { unavailable ->
                item(key = "manual_payments", contentType = "section") {
                    SectionCard(title = stringResource(R.string.rentals_payments), contentPadding = true) {
                        Text(stringResource(
                            if (unavailable == OnlinePayments.Unavailable.NOT_CONFIGURED) R.string.rentals_worklist_payment_unconfigured
                            else R.string.rentals_worklist_payment_disabled,
                        ))
                    }
                }
            }
            // Invoices only appear once they exist; recording payments and invoicing live in the bar / overflow.
            if (invoices.isNotEmpty()) item(key = "invoices", contentType = "section") {
                SectionCard(title = stringResource(R.string.rentals_invoices)) {
                    invoices.forEachIndexed { i, inv ->
                        if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                        ListItem(
                            colors = cardListItemColors(),
                            leadingContent = { LeadingBadge(Icons.Default.Description, StatusTone.Info) },
                            headlineContent = { Text(inv.number ?: stringResource(R.string.rentals_invoice_draft)) },
                            supportingContent = { Text(invoiceStatusText(inv.status)) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatMoney(Money.of(inv.totalMinor, inv.currency)), style = MaterialTheme.typography.bodyMedium)
                                    SyncStateBadge(inv.sync)
                                }
                            },
                        )
                    }
                }
            }
            fees?.let { f -> item(key = "fees", contentType = "section") { FeeBreakdownCard(f) } }
            if (handovers.isNotEmpty()) {
                item(key = "history", contentType = "section") {
                    SectionCard(title = stringResource(R.string.rentals_order_history), contentPadding = true) {
                        handovers.forEachIndexed { i, h ->
                            val out = h.kind == "checkout"
                            TimelineEntry(
                                title = stringResource(if (out) R.string.rentals_action_checkout else R.string.rentals_action_checkin),
                                icon = if (out) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                                tone = if (out) StatusTone.Accent else StatusTone.Success,
                                time = formatDateTime(h.at, zone),
                                isFirst = i == 0,
                                isLast = i == handovers.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showPayment && o != null && !o.isLegacy) {
        PaymentSheet(
            orderId = o.id,
            currency = o.currency,
            canRefund = canRefund,
            suggested = o.balance?.takeIf { it.minor > 0 },
            onDismiss = { showPayment = false },
            onSave = {
                viewModel.recordPayment(it)
                showPayment = false
            },
        )
    }
    if (showExtend && o != null && !o.isLegacy) {
        ExtendDialog(o.endAt ?: Instant.now(), zone, onDismiss = { showExtend = false }) { end ->
            viewModel.action("extend", endAt = end); showExtend = false
        }
    }
    bulkReturn?.let { (line, out) ->
        BulkReturnDialog(line, out, onDismiss = { bulkReturn = null }) { counts, notes ->
            bulkReturn = null
            wave2.bulkReturn(line, counts, notes)
        }
    }
    confirmAction?.let { a ->
        val cancel = a == "cancel"
        ReasonDialog(
            title = stringResource(if (cancel) R.string.rentals_cancel_order_title else R.string.rentals_no_show_title),
            destructive = true,
            confirmLabel = stringResource(if (cancel) R.string.rentals_cancel_order_confirm else R.string.rentals_no_show_confirm),
            dismissLabel = stringResource(R.string.rentals_keep_order),
            required = cancel,
            onDismiss = { confirmAction = null },
        ) { reason ->
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
                SyncStateBadge(o.sync)
            }
            if (o.isPending) {
                Text(
                    stringResource(R.string.rentals_draft_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Column(Modifier.padding(vertical = AvagoSpacing.sm)) {
            LabelValue(stringResource(R.string.rentals_from), formatDateTime(o.startAt, zone))
            LabelValue(stringResource(R.string.rentals_to), formatDateTime(o.endAt, zone))
            o.holdExpiresAt?.let { LabelValue(stringResource(R.string.rentals_hold_expires), formatDateTime(it, zone)) }
        }
        CardDivider()
        // The one place money is shown on this screen: Total Â· Paid Â· Balance (+ deposit).
        MoneyStats(
            listOf(
                Triple(stringResource(R.string.rentals_total), formatMoney(o.total) + if (o.isPending) " *" else "", null),
                Triple(stringResource(R.string.rentals_paid), formatMoney(o.paid), null),
                Triple(
                    stringResource(R.string.rentals_balance),
                    formatMoney(o.balance),
                    if (orderOwes(o)) StatusTone.Warning else null,
                ),
            ),
        )
        o.depositRequired?.let {
            LabelValue(stringResource(R.string.rentals_deposit), formatMoney(it), Modifier.padding(bottom = AvagoSpacing.sm))
        }
    }
}

/**
 * Equal-width money figures (label over value). Falls back to stacked label/value rows at large
 * font scales so amounts never truncate.
 */
@Composable
internal fun MoneyStats(stats: List<Triple<String, String, StatusTone?>>) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.padding(vertical = AvagoSpacing.sm)) {
            stats.forEach { (label, value, tone) -> LabelValue(label, value, emphasise = tone != null) }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.md)) {
        stats.forEach { (label, value, tone) ->
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xxs)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = tone?.let { toneColors(it).content } ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}


/**
 * Sticky bottom bar with the single next step ([primaryOrderAction]) and, when a balance is
 * open, "Collect payment" as the tonal secondary (or the primary when no lifecycle step is left).
 * Everything else lives in the top-bar overflow ([orderOverflow]).
 */
@Composable
private fun OrderActionBar(
    o: OrderView, onCollect: () -> Unit, onAction: (String) -> Unit,
    allowedActions: List<String>, canRecordPayment: Boolean,
) {
    val primary = primaryOrderAction(allowedActions)
    val owes = orderOwes(o) && canRecordPayment
    if (primary == null && !owes) return
    BottomActionBar {
        if (owes) {
            val label = stringResource(R.string.rentals_collect_payment)
            if (primary == null) {
                Button(onClick = onCollect, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                    Text(label, Modifier.padding(start = AvagoSpacing.sm), textAlign = TextAlign.Center, maxLines = 2)
                }
            } else {
                FilledTonalButton(onClick = onCollect, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                    Text(label, Modifier.padding(start = AvagoSpacing.sm), textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        primary?.let { a ->
            Button(onClick = { onAction(a) }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                Icon(orderActionIcon(a), null, Modifier.size(18.dp))
                Text(stringResource(actionLabel(a)), Modifier.padding(start = AvagoSpacing.sm), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

private fun orderOwes(o: OrderView): Boolean = OrderWorklist.canRecordPayment(o) && (o.balance?.minor ?: 0) > 0

/**
 * Overflow entries for an order: remaining lifecycle steps, Record payment (when not already in
 * the bar), Create invoice, then Cancel / No-show in the red destructive group.
 */
@Composable
private fun orderOverflow(
    o: OrderView, canSwap: Boolean, onAction: (String) -> Unit, onPayment: () -> Unit, onInvoice: () -> Unit,
    allowedActions: List<String>, canRecordPayment: Boolean, canInvoice: Boolean,
): List<MenuAction> {
    if (o.isLegacy) return emptyList()
    val all = allowedActions.filter { it != "swap" || canSwap }
    val primary = primaryOrderAction(all)
    val secondary = all.filter { it != primary && it !in DESTRUCTIVE_ORDER_ACTIONS }
    val destructive = all.filter { it in DESTRUCTIVE_ORDER_ACTIONS }
    return buildList {
        secondary.forEach { a -> add(MenuAction(stringResource(actionLabel(a)), orderActionIcon(a)) { onAction(a) }) }
        if (canRecordPayment && !orderOwes(o)) add(MenuAction(stringResource(R.string.rentals_record_payment), Icons.Default.Payments, onClick = onPayment))
        if (canInvoice) add(MenuAction(stringResource(R.string.rentals_create_invoice), Icons.Default.Description, onClick = onInvoice))
        destructive.forEach { a -> add(MenuAction(stringResource(actionLabel(a)), orderActionIcon(a), destructive = true) { onAction(a) }) }
    }
}

private fun orderActionIcon(a: String): ImageVector = when (a) {
    "checkout" -> Icons.AutoMirrored.Filled.Logout
    "checkin" -> Icons.AutoMirrored.Filled.Login
    "close" -> Icons.Default.TaskAlt
    "reserve" -> Icons.Default.EventAvailable
    "hold" -> Icons.Default.PauseCircle
    "quote" -> Icons.Default.Description
    "extend" -> Icons.Default.EditCalendar
    "swap" -> Icons.Default.SwapHoriz
    "off-rent" -> Icons.Default.EventBusy
    "no-show" -> Icons.Default.PersonOff
    "cancel" -> Icons.Default.Block
    else -> Icons.Default.MoreHoriz
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
internal fun ReasonDialog(
    title: String,
    destructive: Boolean = false,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
    required: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val danger = toneColors(StatusTone.Danger).content
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (destructive) {
            { Icon(Icons.Default.Warning, null, tint = danger) }
        } else {
            null
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                reason, { reason = it },
                label = { Text(stringResource(R.string.rentals_reason)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim().takeIf { it.isNotEmpty() }) }, enabled = !required || reason.isNotBlank()) {
                // Destructive confirm names the action in red (colour plus wording).
                Text(confirmLabel ?: stringResource(R.string.rentals_ok), color = if (destructive) danger else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel ?: stringResource(R.string.rentals_cancel)) } },
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
 * Manual payment capture (Â§5.1): kind, method, amount, reference, external-terminal card brand
 * and last 4 (never a card number), payer. `provider` stays null â€” gateway-ready, no Stripe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PaymentSheet(
    orderId: String,
    currency: String,
    canRefund: Boolean,
    suggested: Money?,
    onDismiss: () -> Unit,
    onSave: (PaymentDraft) -> Unit,
) {
    var kind by remember { mutableStateOf("payment") }
    var method by remember { mutableStateOf("cash") }
    var amount by remember { mutableStateOf(suggested?.let { MoneyFormatter.toInput(it) } ?: "") }
    var reference by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var payerName by remember { mutableStateOf("") }
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
                PaymentDraft(
                    orderId = orderId,
                    kind = kind,
                    method = method,
                    amountMinor = parsed.minor,
                    currency = parsed.currency,
                    reference = reference.trim().ifEmpty { null },
                    cardBrand = brand.trim().takeIf { card && it.isNotEmpty() },
                    cardLast4 = last4.takeIf { card && it.length == 4 },
                    payerName = payerName.trim().ifEmpty { null },
                    isAuthorization = card && authorization,
                    notes = notes.trim().ifEmpty { null },
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
                PAYMENT_KINDS.filter { canRefund || it !in REFUND_KINDS }.forEach { k ->
                    FilterChip(kind == k, { kind = k }, { Text(stringResource(ledgerKindLabel(k))) }, Modifier.heightIn(min = MinTouch))
                }
            }
            FieldLabel(stringResource(R.string.rentals_payment_method))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAYMENT_METHODS.forEach { m ->
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
