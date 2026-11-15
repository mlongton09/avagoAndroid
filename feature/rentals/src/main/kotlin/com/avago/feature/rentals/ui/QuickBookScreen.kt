package com.avago.feature.rentals.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.core.network.rentals.RentalsApi
import com.avago.core.ui.components.BottomActionBar
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentals.data.RentalRepository
import com.avago.feature.rentals.data.RentalRequests
import com.avago.feature.rentals.domain.Availability
import com.avago.feature.rentals.domain.ClassView
import com.avago.feature.rentals.domain.CustomerView
import com.avago.feature.rentals.domain.LocalSchedule
import com.avago.feature.rentals.domain.obj
import com.avago.feature.rentals.domain.InstantBookCheck
import com.avago.feature.rentals.domain.PricingDisplay
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import timber.log.Timber

data class QuickBookForm(
    val assetId: String? = null,
    val classId: String? = null,
    val customer: CustomerView? = null,
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
)

data class UnitOption(val assetId: String, val name: String, val classId: String?, val timeZone: String?, val locationId: String?)

@OptIn(FlowPreview::class)
@HiltViewModel
class QuickBookViewModel @Inject constructor(
    private val repo: RentalRepository,
    private val api: RentalsApi,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val initialAsset: String? = savedState.get<String>("assetId")?.takeIf { it.isNotBlank() }
    private val initialStart: Long? = savedState.get<String>("start")?.toLongOrNull()
    private val initialEnd: Long? = savedState.get<String>("end")?.toLongOrNull()
    private val initialCustomer: String? = savedState.get<String>("customerId")?.takeIf { it.isNotBlank() }

    private val _form: MutableStateFlow<QuickBookForm> = MutableStateFlow(
        defaultWindow(ZoneId.systemDefault()).let { (s, e) ->
            QuickBookForm(
                assetId = initialAsset,
                start = initialStart?.let(Instant::ofEpochMilli) ?: s,
                end = initialEnd?.let(Instant::ofEpochMilli) ?: e,
                zone = ZoneId.systemDefault(),
            )
        },
    )
    val form: StateFlow<QuickBookForm> = _form.asStateFlow()

    val units: StateFlow<List<UnitOption>> = repo.records(RentalEntity.FLEET)
        .map { rows -> rows.mapNotNull(LocalFleet::unitOf).map { UnitOption(it.assetId, it.name, it.classId, it.timeZone, it.locationId) }.sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val classes: StateFlow<List<ClassView>> = repo.records(RentalEntity.CLASS)
        .map { rows -> rows.map(ClassView::of).sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customers: StateFlow<List<CustomerView>> = repo.records(RentalEntity.CUSTOMER)
        .map { rows -> rows.map(CustomerView::of).sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val rateCards = repo.records(RentalEntity.RATE_CARD).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val fleetRecords = repo.fleetInputs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Adopt the unit's location time zone (§8) once the fleet is loaded.
        viewModelScope.launch {
            val u = units.first { list -> initialAsset == null || list.any { it.assetId == initialAsset } }
            initialAsset?.let { id -> u.firstOrNull { it.assetId == id }?.let(::selectUnit) }
        }
        // Book from a customer (swipe / detail): preselect them once the customer list is loaded.
        initialCustomer?.let { id ->
            viewModelScope.launch {
                val c = customers.first { list -> list.any { it.id == id } }.first { it.id == id }
                if (_form.value.customer == null) selectCustomer(c)
            }
        }
    }

    /** Offline estimate; replaced by the server preview when online. */
    val preview: StateFlow<PricingDisplay?> = combine(_form, rateCards, units) { f, cards, us -> localPreview(f, cards, us) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _serverPreview = MutableStateFlow<PricingDisplay?>(null)
    val serverPreview: StateFlow<PricingDisplay?> = _serverPreview.asStateFlow()

    /** Would a renter booking this online be confirmed instantly? Null offline or before a unit/class is picked. */
    private val _instant = MutableStateFlow<InstantBookCheck?>(null)
    val instant: StateFlow<InstantBookCheck?> = _instant.asStateFlow()

    /** Offline conflict check against synced allocations. */
    val conflict: StateFlow<Availability.UnitResult?> = combine(_form, fleetRecords) { f, recs ->
        val id = f.assetId ?: return@combine null
        if (!f.end.isAfter(f.start)) return@combine null
        Availability.unit(id, LocalSchedule.busy(recs), f.start, f.end, Instant.now()).takeIf { !it.available }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved: SharedFlow<String> = _saved
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors

    init {
        viewModelScope.launch {
            _form.debounce(600).collect { f ->
                _serverPreview.value = null
                _instant.value = null
                if (!f.end.isAfter(f.start) || (f.assetId == null && f.classId == null)) return@collect
                val acc = repo.accountId.first() ?: return@collect
                try {
                    val res = api.pricingPreview(acc, RentalRequests.pricingPreview(input(f, null)))
                    _serverPreview.value = PricingDisplay.fromServer(res)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.d(e, "pricing preview offline")
                }
                try {
                    _instant.value = InstantBookCheck.from(api.instantBookEvaluate(acc, RentalRequests.instantBookEvaluate(input(f, null))))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Older servers (404) and staff without rental.view simply hide the badge.
                    Timber.d(e, "instant-book evaluate unavailable")
                }
            }
        }
    }

    private fun cardFor(classId: String?, locationId: String?, cards: List<RentalRecordEntity>): RentalRecordEntity? =
        cards.filter { it.classId == classId }.sortedBy { if (it.locationId == locationId) 0 else if (it.locationId == null) 1 else 2 }.firstOrNull()

    private fun localPreview(f: QuickBookForm, cards: List<RentalRecordEntity>, us: List<UnitOption>): PricingDisplay? {
        val unit = us.firstOrNull { it.assetId == f.assetId }
        val classId = f.classId ?: unit?.classId ?: return null
        val card = cardFor(classId, unit?.locationId, cards) ?: return null
        return PricingDisplay.estimate(card.obj, unit?.name ?: classes.value.firstOrNull { it.id == classId }?.name ?: "", f.start, f.end)
    }

    fun selectUnit(u: UnitOption?) {
        _form.value = _form.value.copy(assetId = u?.assetId, classId = u?.classId ?: _form.value.classId, zone = zoneOf(u?.timeZone))
    }
    fun selectClass(id: String?) { _form.value = _form.value.copy(classId = id, assetId = null) }
    fun selectCustomer(c: CustomerView?) { _form.value = _form.value.copy(customer = c) }
    fun setStart(t: Instant) {
        val f = _form.value
        val len = java.time.Duration.between(f.start, f.end).takeIf { !it.isNegative && !it.isZero } ?: java.time.Duration.ofDays(1)
        _form.value = f.copy(start = t, end = if (f.end.isAfter(t)) f.end else t.plus(len))
    }
    fun setEnd(t: Instant) { _form.value = _form.value.copy(end = t) }

    private fun currencyFor(f: QuickBookForm): String {
        val unit = units.value.firstOrNull { it.assetId == f.assetId }
        val classId = f.classId ?: unit?.classId
        return preview.value?.currency
            ?: classes.value.firstOrNull { it.id == classId }?.currency
            ?: runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
    }

    private fun input(f: QuickBookForm, action: String?): RentalRepository.BookingInput {
        val unit = units.value.firstOrNull { it.assetId == f.assetId }
        return RentalRepository.BookingInput(
            customerId = f.customer?.id,
            customerName = f.customer?.name,
            classId = f.classId ?: unit?.classId,
            assetId = f.assetId,
            assetName = unit?.name,
            locationId = unit?.locationId,
            currency = currencyFor(f),
            start = f.start,
            end = f.end,
            action = action,
            estimate = (serverPreview.value ?: preview.value)?.total,
            zoneId = f.zone.id,
        )
    }

    fun book(action: String?) = viewModelScope.launch {
        val f = _form.value
        try {
            _saved.emit(repo.createBooking(input(f, action)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "quick book failed")
            _errors.emit(e.message ?: e.javaClass.simpleName)
        }
    }
}

/**
 * Quick book (§14) as grouped sections — What · Who · When · Price — with the booking actions
 * in a sticky bottom bar (Reserve primary, Hold secondary, Quote / Save draft in the overflow).
 * M3 pickers run in the location ZoneId; the price preview updates live.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickBookScreen(
    onBack: () -> Unit,
    onBooked: (orderId: String) -> Unit,
    onNewCustomer: () -> Unit,
    viewModel: QuickBookViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val classes by viewModel.classes.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val local by viewModel.preview.collectAsStateWithLifecycle()
    val server by viewModel.serverPreview.collectAsStateWithLifecycle()
    val instant by viewModel.instant.collectAsStateWithLifecycle()
    val conflict by viewModel.conflict.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.saved.collect {
            haptics.success()
            onBooked(it)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect {
            busy = false
            haptics.error()
            snackbar.showSnackbar(it)
        }
    }
    val periodOk = form.end.isAfter(form.start)
    val valid = periodOk && (form.assetId != null || form.classId != null) && !busy
    val book: (String?) -> Unit = { action ->
        busy = true
        viewModel.book(action)
    }

    Scaffold(
        topBar = { RentalsTopBar(stringResource(R.string.rentals_quick_book), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar(Modifier.imePadding()) {
                Box {
                    IconButton(onClick = { menu = true }, enabled = valid, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.rentals_more_actions))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rentals_action_quote)) },
                            leadingIcon = { Icon(Icons.Default.Description, null) },
                            onClick = { menu = false; book("quote") },
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rentals_save_draft)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; book(null) },
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                    }
                }
                OutlinedButton(onClick = { book("hold") }, enabled = valid, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_action_hold), textAlign = TextAlign.Center)
                }
                Button(onClick = { book("reserve") }, enabled = valid, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.EventAvailable, null, Modifier.size(18.dp))
                    }
                    Text(stringResource(R.string.rentals_action_reserve), Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionCard(title = stringResource(R.string.rentals_unit), contentPadding = true) {
                Picker(
                    label = stringResource(R.string.rentals_unit),
                    selected = units.firstOrNull { it.assetId == form.assetId }?.name ?: stringResource(R.string.rentals_any_unit),
                    options = listOf<Pair<String, () -> Unit>>(stringResource(R.string.rentals_any_unit) to { viewModel.selectUnit(null) }) +
                        units.map { u -> u.name to { viewModel.selectUnit(u) } },
                )
                if (form.assetId == null && classes.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        classes.forEach { c ->
                            FilterChip(
                                form.classId == c.id, { viewModel.selectClass(c.id) }, { Text(c.name) }, Modifier.heightIn(min = MinTouch),
                                leadingIcon = if (form.classId == c.id) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (form.classId == null) {
                        Text(
                            stringResource(R.string.rentals_pick_unit_or_class),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            SectionCard(title = stringResource(R.string.rentals_customer), contentPadding = true) {
                Picker(
                    label = stringResource(R.string.rentals_customer),
                    selected = form.customer?.name ?: stringResource(R.string.rentals_walk_in),
                    options = listOf<Pair<String, () -> Unit>>(stringResource(R.string.rentals_walk_in) to { viewModel.selectCustomer(null) }) +
                        customers.filter { !it.doNotRent }.map { c -> c.name to { viewModel.selectCustomer(c) } },
                )
                TextButton(onClick = onNewCustomer, modifier = Modifier.heightIn(min = MinTouch)) {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.rentals_new_customer), Modifier.padding(start = 8.dp))
                }
            }
            SectionCard(title = stringResource(R.string.rentals_period), contentPadding = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateTimeField(stringResource(R.string.rentals_from), form.start, form.zone, viewModel::setStart)
                    DateTimeField(stringResource(R.string.rentals_to), form.end, form.zone, viewModel::setEnd)
                    Text(
                        stringResource(R.string.rentals_time_zone, form.zone.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!periodOk) {
                        ErrorBanner(stringResource(R.string.rentals_error_end_before_start), tone = StatusTone.Danger)
                    }
                    conflict?.let { c ->
                        ErrorBanner(
                            c.nextFreeAt?.let { stringResource(R.string.rentals_conflict_next_free, formatDateTime(it, form.zone)) }
                                ?: stringResource(R.string.rentals_unavailable),
                            tone = StatusTone.Warning,
                        )
                    }
                }
            }
            PricePreviewCard(server ?: local)
            instant?.let { InstantBookCard(it) }
            Text(
                stringResource(R.string.rentals_booking_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** Live price breakdown (announced politely) with the total emphasised. */
@Composable
internal fun PricePreviewCard(p: PricingDisplay?) {
    SectionCard(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = stringResource(R.string.rentals_price_preview),
        trailing = p?.let {
            {
                StatusPill(
                    stringResource(if (it.confirmed) R.string.rentals_price_confirmed else R.string.rentals_price_estimate),
                    if (it.confirmed) StatusTone.Success else StatusTone.Neutral,
                    icon = if (it.confirmed) Icons.Default.Verified else Icons.Default.Calculate,
                )
            }
        },
    ) {
        if (p == null) {
            CardNote(stringResource(R.string.rentals_price_unavailable))
            return@SectionCard
        }
        Column(Modifier.padding(vertical = 8.dp)) {
            p.rows.forEach { r -> LabelValue("${r.label} × ${r.qty}${r.detail?.let { " ($it)" } ?: ""}", formatMoney(r.amount)) }
            if (p.discount.minor != 0L) LabelValue(stringResource(R.string.rentals_discount), formatMoney(-p.discount))
            p.tax?.let { LabelValue(stringResource(R.string.rentals_tax), formatMoney(it)) }
        }
        CardDivider()
        Column(Modifier.padding(vertical = 8.dp)) {
            LabelValue(stringResource(R.string.rentals_total), formatMoney(p.total), emphasise = true)
            p.deposit?.let { LabelValue(stringResource(R.string.rentals_deposit), formatMoney(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Picker(label: String, selected: String, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, onClick) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onClick(); expanded = false }, modifier = Modifier.heightIn(min = MinTouch))
            }
        }
    }
}
