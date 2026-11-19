package com.avago.feature.rentals.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.RecordSyncState
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SkeletonListItem
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.HandoverTarget
import java.time.Instant
import java.time.ZoneId

/** Fleet board (§11.1): search, status filter chips with counts, adaptive grid, unit actions. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    var showAvailability by rememberSaveable { mutableStateOf(openFindFree) }
    val width = currentWindowAdaptiveInfo().windowSizeClass.minWidthDp
    val columns = when {
        width >= 840 -> 3
        width >= 600 -> 2
        else -> 1
    }
    val zone = ZoneId.systemDefault()
    val refresh = rememberSyncRefresh(viewModel::syncNow)
    val firstLoad = rememberFirstLoad(fleet.isEmpty)

    Scaffold(
        topBar = {
            RentalsTopBar(stringResource(R.string.rentals_fleet), onBack) {
                IconButton(onClick = { showAvailability = true }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.EventAvailable, contentDescription = stringResource(R.string.rentals_availability))
                }
            }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refresh.refreshing,
            onRefresh = refresh.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + AvagoSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
            ) {
                if (refresh.failed) {
                    item(key = "refresh-failed", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") { RefreshFailedBanner(refresh) }
                }
                item(key = "sync", span = { GridItemSpan(maxLineSpan) }, contentType = "sync") {
                    SyncStatusRow(pending, refresh.onRefresh, syncing = refresh.refreshing)
                }
                item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
                    RentalSearchField(query, viewModel::setQuery, stringResource(R.string.rentals_search_fleet))
                }
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }, contentType = "filters") {
                    ChipRow {
                        FilterChip(
                            selected = filter == null,
                            onClick = { viewModel.setStatusFilter(null) },
                            label = { Text(stringResource(R.string.rentals_filter_all, fleet.units.size)) },
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                        FleetStatus.entries.forEach { s ->
                            val label = "${stringResource(fleetStatusLabel(s))} ${fleet.counts[s] ?: 0}"
                            FilterChip(
                                selected = filter == s,
                                onClick = { viewModel.setStatusFilter(if (filter == s) null else s) },
                                label = { Text(label) },
                                leadingIcon = { Icon(fleetStatusIcon(s), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.heightIn(min = MinTouch),
                            )
                        }
                    }
                }
                when {
                    firstLoad -> items(6, key = { "sk$it" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "skeleton" }) { SkeletonListItem() }
                    units.isEmpty() && fleet.isEmpty -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        EmptyState(stringResource(R.string.rentals_fleet_empty), icon = Icons.Default.EventAvailable)
                    }
                    units.isEmpty() -> item(key = "nomatch", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        EmptyState(
                            stringResource(R.string.rentals_no_matches),
                            icon = Icons.Default.SearchOff,
                            actionLabel = stringResource(R.string.rentals_clear_filters),
                            onAction = {
                                viewModel.setQuery("")
                                viewModel.setStatusFilter(null)
                            },
                        )
                    }
                }
                items(units, key = { it.unit.assetId }, contentType = { "unit" }) { r ->
                    FleetUnitCard(
                        r, zone,
                        onOpen = { onOpenAsset(r.unit.assetId) },
                        onBook = { onBook(r.unit.assetId, null, null) },
                        onOpenOrder = (r.current ?: r.next)?.orderId?.let { id -> { onOpenOrder(id) } },
                        onReadiness = onReadiness?.let { f -> { f(r.unit.assetId) } },
                        onHandover = onHandover?.let { f -> HandoverTarget.of(r)?.let { h -> h to { f(h.orderId, h.direction) } } },
                        modifier = Modifier.padding(horizontal = if (columns == 1) AvagoSpacing.cardHorizontal else AvagoSpacing.sm),
                    )
                }
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

/**
 * Unit card: photo thumbnail, name, "class · timing" supporting line and a status pill, then
 * at most one contextual action (handover > book > readiness) with the rest in an overflow
 * menu. The whole card opens the asset.
 */
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
    modifier: Modifier = Modifier,
) {
    val unitZone = zoneOf(r.unit.timeZone ?: zone.id)
    val overdue = r.status == FleetStatus.OVERDUE
    val timing = when {
        r.dueAt != null && r.status in setOf(FleetStatus.ON_RENT, FleetStatus.OVERDUE) ->
            stringResource(R.string.rentals_due_back, formatDateTime(r.dueAt, unitZone))
        r.nextChangeAt != null -> stringResource(R.string.rentals_next_change, formatDateTime(r.nextChangeAt, unitZone))
        else -> null
    }
    // Contextual actions in priority order; the first is the card's single visible action.
    val actions = buildList {
        onHandover?.let { (h, go) ->
            val label = if (h.direction == HandoverTarget.IN) R.string.rentals_action_checkin else R.string.rentals_action_checkout
            val icon = if (h.direction == HandoverTarget.IN) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout
            add(MenuAction(stringResource(label), icon, onClick = go))
        }
        if (r.status == FleetStatus.AVAILABLE || r.status == FleetStatus.RESERVED) {
            add(MenuAction(stringResource(R.string.rentals_book), Icons.Default.EventAvailable, onClick = onBook))
        }
        if (onReadiness != null && (r.status == FleetStatus.NOT_READY || r.checklist?.open == true)) {
            add(MenuAction(stringResource(R.string.rentals_readiness_open), Icons.Default.Checklist, onClick = onReadiness))
        }
        onOpenOrder?.let { add(MenuAction(stringResource(R.string.rentals_open_order), Icons.AutoMirrored.Filled.ReceiptLong, onClick = it)) }
    }
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(AvagoSpacing.md), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
                UnitThumbnail(r.unit.assetId, fleetStatusIcon(r.status), fleetStatusTone(r.status))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xxs)) {
                    Text(r.unit.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val supporting = listOfNotNull(r.unit.className, timing).joinToString(" · ")
                    if (supporting.isNotEmpty()) {
                        Text(
                            supporting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (overdue) toneColors(StatusTone.Danger).content else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FlowRow(
                        Modifier.padding(top = AvagoSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs),
                    ) {
                        FleetStatusChip(r.status)
                        if (r.readinessPending) SyncStateBadge(RecordSyncState.PENDING)
                    }
                }
            }
            if (r.unit.readiness.isNotEmpty() && r.status == FleetStatus.NOT_READY) {
                Text(
                    stringResource(R.string.rentals_not_ready_reasons, readinessReasons(r.unit.readiness)),
                    style = MaterialTheme.typography.bodySmall,
                    color = toneColors(StatusTone.Danger).content,
                )
            }
            actions.firstOrNull()?.let { primary ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    FilledTonalButton(onClick = primary.onClick, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                        Icon(primary.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AvagoSpacing.sm))
                        Text(primary.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    OverflowMenu(actions.drop(1))
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
            Row(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun UnitResultRow(name: String, available: Boolean, nextFree: Instant?, zone: ZoneId, onBook: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch).padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeadingBadge(
            if (available) fleetStatusIcon(FleetStatus.AVAILABLE) else Icons.Default.Schedule,
            if (available) StatusTone.Success else StatusTone.Neutral,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (available) {
                StatusPill(stringResource(R.string.rentals_status_available), StatusTone.Success, Modifier.padding(top = 2.dp))
            } else {
                Text(
                    nextFree?.let { stringResource(R.string.rentals_next_free, formatDateTime(it, zone)) } ?: stringResource(R.string.rentals_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (available) FilledTonalButton(onClick = onBook, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_book)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

/** − qty + stepper with 48dp tonal buttons; announced as one value. */
@Composable
internal fun QtyStepper(qty: Int, onChange: (Int) -> Unit) {
    val label = stringResource(R.string.rentals_qty_value, qty)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        FilledTonalIconButton(onClick = { onChange(qty - 1) }, enabled = qty > 1, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.Remove, stringResource(R.string.rentals_decrease))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onChange(qty + 1) }, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Default.Add, stringResource(R.string.rentals_increase))
        }
    }
}