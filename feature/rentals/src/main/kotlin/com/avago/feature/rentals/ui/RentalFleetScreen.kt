package com.avago.feature.rentals.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.HandoverTarget
import java.time.Instant
import java.time.ZoneId

/** Fleet board (§11.1): status chips with counts, search, adaptive grid, unit actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RentalFleetScreen(
    initialStatus: FleetStatus?,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onBook: (assetId: String?, start: Instant?, end: Instant?) -> Unit,
    onOpenOrder: (String) -> Unit,
    onReadiness: ((assetId: String) -> Unit)? = null,
    onHandover: ((orderId: String, direction: String) -> Unit)? = null,
    /** Open the "find free units" sheet immediately (home → Find free units). */
    openFindFree: Boolean = false,
    viewModel: RentalsOverviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialStatus) { viewModel.setStatusFilter(initialStatus) }
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    val units by viewModel.visibleUnits.collectAsStateWithLifecycle()
    val filter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var showAvailability by rememberSaveable { mutableStateOf(openFindFree) }
    val width = currentWindowAdaptiveInfo().windowSizeClass.minWidthDp
    val columns = when {
        width >= 840 -> 3
        width >= 600 -> 2
        else -> 1
    }
    val zone = ZoneId.systemDefault()

    Scaffold(
        topBar = {
            RentalsTopBar(stringResource(R.string.rentals_fleet), onBack) {
                IconButton(onClick = { showAvailability = true }) {
                    Icon(Icons.Default.EventAvailable, contentDescription = stringResource(R.string.rentals_availability))
                }
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pad.calculateTopPadding(), bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { LastSyncedRow(lastSynced, queue.size, viewModel::syncNow) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.rentals_search_fleet)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val all = stringResource(R.string.rentals_filter_all, fleet.units.size)
                    FilterChip(
                        selected = filter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text(all) },
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                    FleetStatus.entries.forEach { s ->
                        val label = "${stringResource(fleetStatusLabel(s))} ${fleet.counts[s] ?: 0}"
                        FilterChip(
                            selected = filter == s,
                            onClick = { viewModel.setStatusFilter(if (filter == s) null else s) },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                    }
                }
            }
            if (units.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(if (fleet.isEmpty) R.string.rentals_fleet_empty else R.string.rentals_no_matches))
                }
            }
            items(units, key = { it.unit.assetId }) { r ->
                FleetUnitCard(
                    r, zone,
                    onOpen = { onOpenAsset(r.unit.assetId) },
                    onBook = { onBook(r.unit.assetId, null, null) },
                    onOpenOrder = (r.current ?: r.next)?.orderId?.let { id -> { onOpenOrder(id) } },
                    onReadiness = onReadiness?.let { f -> { f(r.unit.assetId) } },
                    onHandover = onHandover?.let { f -> HandoverTarget.of(r)?.let { h -> h to { f(h.orderId, h.direction) } } },
                )
            }
        }
    }
    if (showAvailability) {
        AvailabilitySheet(
            viewModel = viewModel,
            onDismiss = { showAvailability = false; viewModel.clearAvailability() },
            onBook = { assetId, from, to -> showAvailability = false; onBook(assetId, from, to) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetUnitCard(
    r: LocalFleet.Result,
    zone: ZoneId,
    onOpen: () -> Unit,
    onBook: () -> Unit,
    onOpenOrder: (() -> Unit)?,
    onReadiness: (() -> Unit)? = null,
    onHandover: Pair<HandoverTarget, () -> Unit>? = null,
) {
    val unitZone = zoneOf(r.unit.timeZone ?: zone.id)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.unit.name, style = MaterialTheme.typography.titleMedium)
                    r.unit.className?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                FleetStatusChip(r.status)
            }
            when {
                r.dueAt != null && r.status in setOf(FleetStatus.ON_RENT, FleetStatus.OVERDUE) ->
                    Text(stringResource(R.string.rentals_due_back, formatDateTime(r.dueAt, unitZone)), style = MaterialTheme.typography.bodySmall)
                r.nextChangeAt != null ->
                    Text(stringResource(R.string.rentals_next_change, formatDateTime(r.nextChangeAt, unitZone)), style = MaterialTheme.typography.bodySmall)
            }
            if (r.unit.readiness.isNotEmpty() && r.status == FleetStatus.NOT_READY) {
                Text(
                    stringResource(R.string.rentals_not_ready_reasons, readinessReasons(r.unit.readiness)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (r.local || r.readinessPending) SyncStateBadge(com.avago.core.data.db.rental.RentalRecordEntity.STATE_PENDING)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onHandover?.let { (h, go) ->
                    val label = if (h.direction == HandoverTarget.IN) R.string.rentals_action_checkin else R.string.rentals_action_checkout
                    FilledTonalButton(onClick = go, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(label)) }
                }
                if (r.status == FleetStatus.AVAILABLE || r.status == FleetStatus.RESERVED) {
                    TextButton(onClick = onBook, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_book)) }
                }
                if (onReadiness != null && (r.status == FleetStatus.NOT_READY || r.checklist?.open == true)) {
                    TextButton(onClick = onReadiness, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_readiness_open)) }
                }
                onOpenOrder?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_open_order)) }
                }
            }
        }
    }
}

/** Availability search (§11.1): offline answer from synced allocations, confirmed online. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AvailabilitySheet(
    viewModel: RentalsOverviewViewModel,
    onDismiss: () -> Unit,
    onBook: (assetId: String?, from: Instant, to: Instant) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val (d0, d1) = remember { defaultWindow(zone) }
    var from by remember { mutableStateOf(d0) }
    var to by remember { mutableStateOf(d1) }
    var qty by remember { mutableIntStateOf(1) }
    var classId by remember { mutableStateOf<String?>(null) }
    val classes by viewModel.classes.collectAsStateWithLifecycle()
    val state by viewModel.availability.collectAsStateWithLifecycle()
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    val names = remember(fleet) { fleet.units.associate { it.unit.assetId to it.unit.name } }
    val valid = to.isAfter(from)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.rentals_availability), style = MaterialTheme.typography.titleLarge)
            DateTimeField(stringResource(R.string.rentals_from), from, zone, { from = it })
            DateTimeField(stringResource(R.string.rentals_to), to, zone, { to = it })
            if (!valid) Text(stringResource(R.string.rentals_error_end_before_start), color = MaterialTheme.colorScheme.error)
            QtyStepper(qty, { qty = it.coerceIn(1, 99) })
            if (classes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(classId == null, { classId = null }, { Text(stringResource(R.string.rentals_any_class)) }, Modifier.heightIn(min = MinTouch))
                    classes.forEach { c ->
                        FilterChip(classId == c.id, { classId = c.id }, { Text(c.name) }, Modifier.heightIn(min = MinTouch))
                    }
                }
            }
            Button(
                onClick = { viewModel.searchAvailability(AvailabilityQuery(from, to, classId, qty)) },
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.rentals_search)) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.query != null) {
                if (state.server.isNotEmpty()) {
                    state.server.forEach { c ->
                        val price = c.price?.let(::formatMoney) ?: c.priceError?.let { stringResource(R.string.rentals_price_unavailable) }
                        LabelValue(
                            c.name,
                            stringResource(R.string.rentals_free_of_total, c.free, c.total) + (price?.let { " · $it" } ?: ""),
                        )
                    }
                    state.serverUnits.forEach { u ->
                        UnitResultRow(u.name, u.available && u.ready, u.nextFreeAt, zone) { onBook(u.assetId, from, to) }
                    }
                } else {
                    if (state.offline || !state.loading) {
                        Text(
                            stringResource(R.string.rentals_availability_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.local.forEach { (cls, units) ->
                        val name = classes.firstOrNull { it.id == cls.classId }?.name ?: stringResource(R.string.rentals_unclassified)
                        LabelValue(name, stringResource(R.string.rentals_free_of_total, cls.freeUnits, cls.totalUnits))
                        units.forEach { u ->
                            UnitResultRow(names[u.assetId] ?: u.assetId, u.available, u.nextFreeAt, zone) { onBook(u.assetId, from, to) }
                        }
                    }
                }
            }
            Row(Modifier.padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun UnitResultRow(name: String, available: Boolean, nextFree: Instant?, zone: ZoneId, onBook: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (available) stringResource(R.string.rentals_status_available)
                else nextFree?.let { stringResource(R.string.rentals_next_free, formatDateTime(it, zone)) } ?: stringResource(R.string.rentals_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (available) TextButton(onClick = onBook, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_book)) }
    }
    HorizontalDivider()
}

@Composable
internal fun QtyStepper(qty: Int, onChange: (Int) -> Unit) {
    val label = stringResource(R.string.rentals_qty_value, qty)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
        IconButton(onClick = { onChange(qty - 1) }, enabled = qty > 1) { Icon(Icons.Default.Remove, stringResource(R.string.rentals_decrease)) }
        Text(label, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onChange(qty + 1) }) { Icon(Icons.Default.Add, stringResource(R.string.rentals_increase)) }
    }
}
