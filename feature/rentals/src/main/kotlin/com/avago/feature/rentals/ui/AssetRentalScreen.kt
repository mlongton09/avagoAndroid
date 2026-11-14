package com.avago.feature.rentals.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.core.data.rental.MoneyFormatter
import com.avago.feature.rentals.R
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.domain.AssetProfit
import com.avago.feature.rentals.domain.LineView
import com.avago.feature.rentals.domain.OrderView
import com.avago.feature.rentals.domain.RentalEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class AssetBooking(val order: OrderView, val line: LineView)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetRentalViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val dbFactory: DatabaseFactory,
    private val identity: IdentityManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    val assetId: String = requireNotNull(savedState["assetId"])

    private fun <T> perAccount(empty: T, block: suspend (String) -> Flow<T>): Flow<T> =
        identity.activeAccountId.flatMapLatest { acc ->
            if (acc == null) flowOf(empty) else flow { emitAll(block(acc)) }
        }

    val asset: StateFlow<AssetEntity?> = perAccount(null) { acc ->
        dbFactory.get(acc).assetDao().observeAll(acc).map { list -> list.firstOrNull { it.assetId == assetId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val status: StateFlow<LocalFleet.Result?> = repo.fleetInputs()
        .map { LocalFleet.compute(it, Instant.now())[assetId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val bookings: StateFlow<List<AssetBooking>> = combine(
        repo.byAsset(RentalEntity.ORDER_LINE, assetId),
        repo.records(RentalEntity.ORDER),
    ) { lines, orders ->
        val byId = orders.associateBy { it.id }
        lines.mapNotNull { l ->
            val o = l.orderId?.let(byId::get)?.let(OrderView::of) ?: return@mapNotNull null
            AssetBooking(o, LineView.of(l, o.currency))
        }.sortedByDescending { it.line.startAt ?: it.order.startAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profit: StateFlow<AssetProfit.Result?> = combine(
        bookings,
        perAccount(emptyList()) { acc -> dbFactory.get(acc).logDao().observeAll(acc).map { logs -> logs.filter { it.assetId == assetId } } },
    ) { bs, logs ->
        val cur = bs.firstOrNull()?.order?.currency ?: logs.firstNotNullOfOrNull { it.currency } ?: return@combine null
        AssetProfit.compute(
            cur,
            bs.map { AssetProfit.Revenue(it.order.status, it.line.amount) },
            logs.map { AssetProfit.Cost(it.cost, it.currency) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages
    val currency = MutableStateFlow(runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD"))

    fun setRentable(enabled: Boolean, day: String, week: String, deposit: String) = viewModelScope.launch {
        val a = asset.value
        val cur = currency.value
        try {
            val dayRate = MoneyFormatter.parse(day, cur)?.takeIf { it.minor > 0 }
            if (enabled && dayRate == null) {
                _messages.emit("day rate"); return@launch
            }
            repo.setRentable(
                assetId = assetId,
                assetName = a?.name ?: assetId,
                assetType = a?.assetType,
                enabled = enabled,
                dayRate = dayRate,
                weekRate = MoneyFormatter.parse(week, cur)?.takeIf { it.minor > 0 },
                deposit = MoneyFormatter.parse(deposit, cur)?.takeIf { it.minor > 0 },
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
    onOpenLegacy: (assetId: String) -> Unit,
    viewModel: AssetRentalViewModel = hiltViewModel(),
) {
    val asset by viewModel.asset.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val profit by viewModel.profit.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val dayRequired = stringResource(R.string.rentals_day_rate_required)
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(if (it == "day rate") dayRequired else it) } }
    var day by remember { mutableStateOf("") }
    var week by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    val zone = zoneOf(status?.unit?.timeZone)

    Scaffold(
        topBar = { RentalsTopBar(asset?.name ?: stringResource(R.string.rentals_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val s = status
            if (s != null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FleetStatusChip(s.status)
                    if (s.local) SyncStateBadge(com.avago.core.data.db.rental.RentalRecordEntity.STATE_PENDING)
                }
                s.unit.className?.let { LabelValue(stringResource(R.string.rentals_class), it) }
                s.dueAt?.let { LabelValue(stringResource(R.string.rentals_due), formatDateTime(it, zone)) }
                s.nextChangeAt?.let { LabelValue(stringResource(R.string.rentals_next_change_label), formatDateTime(it, zone)) }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onBook(viewModel.assetId) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_book)) }
                    OutlinedButton(onClick = { viewModel.setRentable(false, "", "", "") }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.rentals_disable_rentable))
                    }
                }
            } else {
                SectionHeader(stringResource(R.string.rentals_make_rentable))
                Text(stringResource(R.string.rentals_make_rentable_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        currency, { viewModel.currency.value = it.uppercase().take(3) },
                        label = { Text(stringResource(R.string.rentals_currency)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    MoneyField(day, stringResource(R.string.rentals_day_rate, currency)) { day = it }
                    MoneyField(week, stringResource(R.string.rentals_week_rate, currency)) { week = it }
                    MoneyField(deposit, stringResource(R.string.rentals_deposit_amount, currency)) { deposit = it }
                    Button(
                        onClick = { viewModel.setRentable(true, day, week, deposit) },
                        enabled = currency.length == 3 && MoneyFormatter.parse(day, currency)?.let { it.minor > 0 } == true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.rentals_make_rentable)) }
                }
            }

            profit?.let { p ->
                SectionHeader(stringResource(R.string.rentals_profit))
                ProfitCard(p)
            }

            SectionHeader(stringResource(R.string.rentals_bookings)) {
                TextButton(onClick = { onOpenLegacy(viewModel.assetId) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_legacy_rentals))
                }
            }
            if (bookings.isEmpty()) EmptyState(stringResource(R.string.rentals_bookings_empty))
            bookings.forEach { b ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onOpenOrder(b.order.id) },
                    overlineContent = { Text(stringResource(orderStatusLabel(b.order.status))) },
                    headlineContent = { Text(listOfNotNull(b.order.number ?: stringResource(R.string.rentals_draft_order), b.order.customerName).joinToString(" · ")) },
                    supportingContent = { Text("${formatDateTime(b.line.startAt ?: b.order.startAt, zone)} → ${formatDateTime(b.line.endAt ?: b.order.endAt, zone)}") },
                    trailingContent = { Text(formatMoney(b.line.amount)) },
                )
            }
        }
    }
}

@Composable
private fun MoneyField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Revenue vs costs bars (chart style guide: palette blue / orange, value axis from 0, labels as text). */
@Composable
private fun ProfitCard(p: AssetProfit.Result) {
    val dark = isSystemInDarkTheme()
    val blue = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val orange = if (dark) Color(0xFFFB923C) else Color(0xFFC2410C)
    val revenue = formatMoney(p.revenue)
    val costs = formatMoney(p.costs)
    val profitText = formatMoney(p.profit)
    val summary = stringResource(R.string.rentals_profit_summary, revenue, costs, profitText)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(profitText, style = MaterialTheme.typography.headlineSmall, color = if (p.profit.minor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            p.marginPct?.let { Text(stringResource(R.string.rentals_margin, it), style = MaterialTheme.typography.bodySmall) }
            val max = maxOf(p.revenue.minor, p.costs.minor, 1L).toFloat()
            Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = summary }) {
                val h = size.height / 2 - 4f
                drawRoundRect(blue, Offset(0f, 0f), Size(size.width * (p.revenue.minor / max), h), CornerRadius(4f, 4f))
                drawRoundRect(orange, Offset(0f, h + 8f), Size(size.width * (p.costs.minor / max), h), CornerRadius(4f, 4f))
            }
            LabelValue(stringResource(R.string.rentals_revenue), revenue)
            LabelValue(stringResource(R.string.rentals_costs), costs)
            if (p.skipped > 0) Text(stringResource(R.string.rentals_profit_skipped, p.skipped), style = MaterialTheme.typography.bodySmall)
        }
    }
}
