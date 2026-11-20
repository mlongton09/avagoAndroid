package com.avago.feature.rentals.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.ActivityFilter
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.Asset
import com.avago.core.data.domain.AssetMeter
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.domain.RentalRates
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.RentalSettings
import com.avago.core.data.domain.RentalSettingsRepository
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.MoneyFormatter
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.isDarkScheme
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.AssetProfit
import com.avago.feature.rentals.domain.HandoverTarget
import com.avago.feature.rentals.domain.LineView
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.RentalMeters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

data class AssetBooking(val order: OrderView, val line: LineView)

@HiltViewModel
class AssetRentalViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val assets: AssetRepository,
    activities: ActivityRepository,
    settings: RentalSettingsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val assetId: String = requireNotNull(savedState["assetId"])

    val asset: StateFlow<Asset?> = assets.observeAsset(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val status: StateFlow<LocalFleet.Result?> = repo.observeFleet()
        .map { LocalFleet.compute(it, Instant.now())[assetId] }
        // Walks every rental row: keep it off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val bookings: StateFlow<List<AssetBooking>> = combine(
        repo.observeLines(),
        repo.observeOrders(),
    ) { lines, orders ->
        val byId = orders.associateBy { it.id }
        lines.filter { it.assetId == assetId }.mapNotNull { l ->
            val o = byId[l.orderId]?.let(OrderView::of) ?: return@mapNotNull null
            AssetBooking(o, LineView.of(l, o.currency))
        }.sortedByDescending { it.line.startAt ?: it.order.startAt }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profit: StateFlow<AssetProfit.Result?> = combine(
        bookings,
        activities.observeActivities(ActivityFilter(assetId = assetId)),
    ) { bs, logs ->
        val cur = bs.firstOrNull()?.order?.currency ?: logs.firstNotNullOfOrNull { it.currency } ?: return@combine null
        AssetProfit.compute(
            cur,
            bs.map { AssetProfit.Revenue(it.order.status, it.line.amount) },
            logs.map { AssetProfit.Cost(it.totalCost.toDouble(), it.currency) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The unit's meters; hand-over readings land on the one of the class meter kind. */
    val meters: StateFlow<List<AssetMeter>> = assets.observeMeters(assetId)
        .map(RentalMeters::ordered)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rentalSettings: StateFlow<RentalSettings> = settings.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RentalSettings())

    /** Account distance unit (`rental` settings), the default for a new distance meter. */
    val distanceUnit: StateFlow<String> = rentalSettings.map { it.distanceUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RentalSettings().distanceUnit)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages

    /** Rates currency; the account default until the user types one. */
    val currency = MutableStateFlow(RentalSettings().defaultCurrency)
    private var currencyEdited = false

    init {
        viewModelScope.launch {
            rentalSettings.collect { if (!currencyEdited) currency.value = it.defaultCurrency }
        }
    }

    fun editCurrency(value: String) {
        currencyEdited = true
        currency.value = value.uppercase().take(3)
    }

    /** `maint.asset_meters.create` (optimistic; the first meter becomes the default). */
    fun addMeter(kind: String, unit: String) = viewModelScope.launch {
        try {
            assets.createMeter(assetId, kind, unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "addMeter failed")
            _messages.emit(MSG_METER_FAILED)
        }
    }

    /** `rental.assets.enable_rental` with the entered rates, or `disable_rental`. */
    fun setRentable(enabled: Boolean, day: String, week: String, deposit: String) = viewModelScope.launch {
        val cur = currency.value
        try {
            if (!enabled) {
                repo.disableRental(assetId)
                return@launch
            }
            val dayRate = MoneyFormatter.parse(day, cur)?.takeIf { it.minor > 0 } ?: run {
                _messages.emit("day rate"); return@launch
            }
            repo.enableRental(
                assetId,
                RentalRates(
                    dayMinor = dayRate.minor,
                    weekMinor = MoneyFormatter.parse(week, cur)?.minor?.takeIf { it > 0 },
                    depositMinor = MoneyFormatter.parse(deposit, cur)?.minor?.takeIf { it > 0 },
                    currency = cur,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "setRentable failed")
            _messages.emit(e.message ?: e.javaClass.simpleName)
        }
    }
}

/** Asset → Rental tab (§14): status, bookings, make-rentable, per-asset profit. */
@Composable
fun AssetRentalScreen(
    onBack: () -> Unit,
    onBook: (assetId: String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onReadiness: ((assetId: String) -> Unit)? = null,
    onHandover: ((orderId: String, direction: String) -> Unit)? = null,
    viewModel: AssetRentalViewModel = hiltViewModel(),
) {
    val asset by viewModel.asset.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val profit by viewModel.profit.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val dayRequired = stringResource(R.string.rentals_day_rate_required)
    val meterFailed = stringResource(R.string.rentals_meter_add_failed)
    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            snackbar.showSnackbar(
                when (it) {
                    "day rate" -> dayRequired
                    MSG_METER_FAILED -> meterFailed
                    else -> it
                },
            )
        }
    }
    val meters by viewModel.meters.collectAsStateWithLifecycle()
    val distanceUnit by viewModel.distanceUnit.collectAsStateWithLifecycle()
    var day by rememberSaveable { mutableStateOf("") }
    var week by rememberSaveable { mutableStateOf("") }
    var deposit by rememberSaveable { mutableStateOf("") }
    val zone = zoneOf(status?.unit?.timeZone)

    val haptics = rememberHaptics()
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var showAllBookings by rememberSaveable { mutableStateOf(false) }
    val s = status
    val handover = if (s != null && onHandover != null) HandoverTarget.of(s) else null
    val needsReadiness = s != null && onReadiness != null && (s.status == FleetStatus.NOT_READY || s.checklist?.open == true)
    val canMakeRentable = currency.length == 3 && MoneyFormatter.parse(day, currency)?.let { it.minor > 0 } == true
    val dayInvalid = day.isNotBlank() && MoneyFormatter.parse(day, currency)?.let { it.minor > 0 } != true

    // One primary per screen: handover > readiness > book. Everything else goes to the overflow.
    val bookAction = MenuAction(stringResource(R.string.rentals_book), Icons.Default.EventAvailable) { onBook(viewModel.assetId) }
    val readinessAction = MenuAction(stringResource(R.string.rentals_readiness_open), Icons.Default.Checklist) { onReadiness?.invoke(viewModel.assetId) }
    val handoverAction = handover?.let { h ->
        val inbound = h.direction == HandoverTarget.IN
        MenuAction(
            stringResource(if (inbound) R.string.rentals_action_checkin else R.string.rentals_action_checkout),
            if (inbound) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
        ) { onHandover?.invoke(h.orderId, h.direction) }
    }
    val primary = handoverAction ?: if (needsReadiness) readinessAction else bookAction
    val overflow = if (s == null) emptyList() else buildList {
        if (primary !== bookAction) add(bookAction)
        if (needsReadiness && primary !== readinessAction) add(readinessAction)
        add(MenuAction(stringResource(R.string.rentals_disable_rentable), Icons.Default.Block, destructive = true) { confirmStop = true })
    }

    Scaffold(
        topBar = {
            RentalsTopBar(asset?.name ?: stringResource(R.string.rentals_title), onBack) {
                OverflowMenu(overflow)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar {
                if (s != null) {
                    Button(onClick = primary.onClick, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                        Icon(primary.icon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(AvagoSpacing.sm))
                        Text(primary.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Button(
                        onClick = { haptics.tick(); viewModel.setRentable(true, day, week, deposit) },
                        enabled = canMakeRentable,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.rentals_make_rentable)) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = AvagoSpacing.sm),
        ) {
            if (s != null) {
                SectionCard(title = stringResource(R.string.rentals_status)) {
                    // Hero: unit photo, name, class and status at a glance.
                    Row(
                        Modifier.padding(AvagoSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UnitThumbnail(viewModel.assetId, fleetStatusIcon(s.status), fleetStatusTone(s.status), size = AvagoSpacing.thumbnailLg)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                            Text(
                                asset?.name ?: s.unit.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            s.unit.className?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                FleetStatusChip(s.status)
                                if (s.readinessPending) SyncStateBadge(RecordSyncState.PENDING)
                            }
                        }
                    }
                    if (s.status == FleetStatus.NOT_READY && s.unit.readiness.isNotEmpty()) {
                        ErrorBanner(
                            stringResource(R.string.rentals_not_ready_reasons, readinessReasons(s.unit.readiness)),
                            modifier = Modifier.padding(bottom = 12.dp),
                            tone = StatusTone.Warning,
                        )
                    }
                    val rows = listOfNotNull(
                        s.dueAt?.let { stringResource(R.string.rentals_due) to formatDateTime(it, zone) },
                        s.nextChangeAt?.let { stringResource(R.string.rentals_next_change_label) to formatDateTime(it, zone) },
                    )
                    if (rows.isNotEmpty()) {
                        CardDivider()
                        Column(Modifier.padding(vertical = 8.dp)) { rows.forEach { (l, v) -> LabelValue(l, v) } }
                    }
                }
            } else {
                SectionCard(title = stringResource(R.string.rentals_make_rentable)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.rentals_make_rentable_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            currency, viewModel::editCurrency,
                            label = { Text(stringResource(R.string.rentals_currency)) },
                            singleLine = true,
                            isError = currency.length != 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MoneyField(
                            day, stringResource(R.string.rentals_day_rate, currency), currency,
                            error = if (dayInvalid) stringResource(R.string.rentals_amount_invalid) else null,
                        ) { day = it }
                        MoneyField(week, stringResource(R.string.rentals_week_rate, currency), currency) { week = it }
                        MoneyField(deposit, stringResource(R.string.rentals_deposit_amount, currency), currency, last = true) { deposit = it }
                    }
                }
            }

            if (s != null) {
                MetersCard(meters, distanceUnit, onAdd = { kind, unit -> haptics.tick(); viewModel.addMeter(kind, unit) })
            }

            profit?.let { p -> ProfitCard(p) }

            SectionCard(
                title = stringResource(R.string.rentals_bookings),
                trailing = if (bookings.isNotEmpty()) {
                    { Text("${bookings.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    null
                },
            ) {
                if (bookings.isEmpty()) {
                    EmptyState(stringResource(R.string.rentals_bookings_empty), icon = Icons.Default.EventBusy)
                }
                bookings.take(if (showAllBookings) bookings.size else PREVIEW_ROWS).forEachIndexed { i, b ->
                    if (i > 0) CardDivider()
                    ListItem(
                        colors = cardListItemColors(),
                        modifier = Modifier.clickable(role = Role.Button) { onOpenOrder(b.order.id) },
                        leadingContent = { LeadingBadge(orderStatusIcon(b.order.status), orderStatusTone(b.order.status)) },
                        headlineContent = {
                            Text(
                                listOfNotNull(b.order.number ?: stringResource(R.string.rentals_draft_order), b.order.customerName).joinToString(" · "),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${formatDateTime(b.line.startAt ?: b.order.startAt, zone)} → ${formatDateTime(b.line.endAt ?: b.order.endAt, zone)}")
                                OrderStatusPill(b.order.status)
                            }
                        },
                        trailingContent = { Text(formatMoney(b.line.amount), style = MaterialTheme.typography.titleSmall) },
                    )
                }
                ShowAllRow(bookings.size, showAllBookings, { showAllBookings = !showAllBookings })
            }
        }
    }
    if (confirmStop) {
        val name = asset?.name ?: s?.unit?.name ?: viewModel.assetId
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = toneColors(StatusTone.Danger).content) },
            title = { Text(stringResource(R.string.rentals_stop_renting_title, name)) },
            text = { Text(stringResource(R.string.rentals_stop_renting_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        haptics.tick()
                        viewModel.setRentable(false, "", "", "")
                    },
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentals_disable_rentable), color = toneColors(StatusTone.Danger).content) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_cancel))
                }
            },
        )
    }
}

private const val MSG_METER_FAILED = "meter"

@Composable
private fun meterKindLabel(kind: String): String = stringResource(
    if (kind == RentalMeters.HOURS) R.string.rentals_meter_hours else R.string.rentals_meter_distance,
)

/** The unit's meters, with "Add meter" for a rental meter kind it does not track yet. */
@Composable
private fun MetersCard(meters: List<AssetMeter>, distanceUnit: String, onAdd: (kind: String, unit: String) -> Unit) {
    val missing = RentalMeters.missing(meters)
    var adding by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.rentals_meters_title)) {
        Text(
            stringResource(R.string.rentals_meters_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
        )
        meters.forEach { m ->
            CardDivider()
            ListItem(
                colors = cardListItemColors(),
                headlineContent = { Text(if (m.kind in RentalMeters.KINDS) meterKindLabel(m.kind) else m.kind) },
                supportingContent = {
                    Text(
                        m.lastValue?.let { "${formatReading(it)} ${meterUnitLabel(m.unit)}" }
                            ?: stringResource(R.string.rentals_meter_no_reading),
                    )
                },
                trailingContent = if (m.isDefault) {
                    { StatusPill(stringResource(R.string.rentals_meter_default), StatusTone.Neutral) }
                } else {
                    null
                },
            )
        }
        if (missing.isNotEmpty()) {
            CardDivider()
            TextButton(
                onClick = { adding = true },
                modifier = Modifier.padding(horizontal = AvagoSpacing.sm).heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.rentals_meter_add)) }
        }
    }
    if (adding && missing.isNotEmpty()) {
        var kind by rememberSaveable { mutableStateOf(missing.first()) }
        var unit by rememberSaveable { mutableStateOf(RentalMeters.unitFor(RentalMeters.DISTANCE, distanceUnit)) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.rentals_meter_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    missing.forEach { k ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouch)
                                .selectable(selected = kind == k, role = Role.RadioButton, onClick = { kind = k }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = kind == k, onClick = null)
                            Text(meterKindLabel(k), modifier = Modifier.padding(start = AvagoSpacing.sm))
                        }
                    }
                    if (kind == RentalMeters.DISTANCE) {
                        Text(
                            stringResource(R.string.rentals_meter_unit_title),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = AvagoSpacing.sm),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                            RentalMeters.DISTANCE_UNITS.forEach { u ->
                                FilterChip(unit == u, { unit = u }, { Text(u) }, Modifier.heightIn(min = MinTouch))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        adding = false
                        onAdd(kind, RentalMeters.unitFor(kind, unit))
                    },
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.rentals_meter_add)) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_cancel))
                }
            },
        )
    }
}

@Composable
private fun MoneyField(value: String, label: String, currency: String, error: String? = null, last: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        prefix = { Text("$currency ") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Revenue vs costs bars (chart style guide: palette blue / orange, value axis from 0, labels as text). */
@Composable
private fun ProfitCard(p: AssetProfit.Result) {
    val dark = isDarkScheme()
    val blue = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val orange = if (dark) Color(0xFFFB923C) else Color(0xFFC2410C)
    val revenue = formatMoney(p.revenue)
    val costs = formatMoney(p.costs)
    val profitText = formatMoney(p.profit)
    val summary = stringResource(R.string.rentals_profit_summary, revenue, costs, profitText)
    val negative = p.profit.minor < 0
    SectionCard(title = stringResource(R.string.rentals_profit)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    profitText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                p.marginPct?.let {
                    StatusPill(
                        stringResource(R.string.rentals_margin, it),
                        if (negative) StatusTone.Danger else StatusTone.Success,
                        icon = if (negative) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                    )
                }
            }
            val max = maxOf(p.revenue.minor, p.costs.minor, 1L).toFloat()
            Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = summary }) {
                val h = size.height / 2 - 4f
                drawRoundRect(blue, Offset(0f, 0f), Size(size.width * (p.revenue.minor / max), h), CornerRadius(8f, 8f))
                drawRoundRect(orange, Offset(0f, h + 8f), Size(size.width * (p.costs.minor / max), h), CornerRadius(8f, 8f))
            }
        }
        CardDivider()
        Column(Modifier.padding(vertical = 8.dp)) {
            LegendValue(blue, stringResource(R.string.rentals_revenue), revenue)
            LegendValue(orange, stringResource(R.string.rentals_costs), costs)
            if (p.skipped > 0) {
                Text(
                    stringResource(R.string.rentals_profit_skipped, p.skipped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Label/value row with a legend swatch tying it to the bar above (label text keeps colour from being the only cue). */
@Composable
private fun LegendValue(color: Color, label: String, value: String) {
    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        LabelValue(label, value, Modifier.weight(1f))
    }
}
