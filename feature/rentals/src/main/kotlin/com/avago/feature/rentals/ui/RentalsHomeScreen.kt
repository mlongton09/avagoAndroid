package com.avago.feature.rentals.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.FleetStatus
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.StatusPill
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.KpiGrid
import com.avago.core.ui.components.KpiItem
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonBlock
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StatusTone
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.rememberHaptics
import com.avago.core.ui.components.toneColors
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.HandoverTarget
import com.avago.feature.rentals.domain.RentalEntity
import com.avago.feature.rentals.domain.ScheduleEvent
import java.time.ZoneId

/** Navigation callbacks of the rentals home, grouped so the screen signature stays readable. */
data class RentalsHomeActions(
    val onOpenFleet: (FleetStatus?) -> Unit,
    val onFindFree: () -> Unit,
    val onOpenSchedule: () -> Unit,
    val onOpenOrders: () -> Unit,
    val onOpenOrder: (String) -> Unit,
    val onOpenCustomers: () -> Unit,
    val onNewBooking: () -> Unit,
    val onScanCheckOut: () -> Unit,
    val onScanCheckIn: () -> Unit,
    val onHandover: (orderId: String, direction: String) -> Unit,
)

/**
 * Rentals landing screen (spec §14), task-first: KPI tiles (on rent / available / overdue / due
 * today) that open the filtered fleet, the counter jobs, today's pickups and returns with a swipe
 * or button for the next step, then fleet tools, operations (slot, permission-gated by the host)
 * and records. Quick book and Scan sit in the thumb zone as floating actions.
 *
 * [onBack] is null when the screen is a top-level destination: the host app bar (menu + title)
 * is shown instead of this screen's own bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalsHomeScreen(
    onBack: (() -> Unit)?,
    actions: RentalsHomeActions,
    operations: (@Composable () -> Unit)? = null,
    viewModel: RentalsOverviewViewModel = hiltViewModel(),
) {
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val refresh = rememberSyncRefresh(lastSynced, viewModel::syncNow)
    val firstLoad = rememberFirstLoad(fleet.isEmpty, lastSynced)

    Scaffold(
        topBar = { if (onBack != null) RentalsTopBar(stringResource(R.string.rentals_title), onBack) },
        // As a top-level tab the host Scaffold already applies the system-bar insets.
        contentWindowInsets = if (onBack == null) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        floatingActionButton = {
            // The one primary action of the rentals home; scan jobs live in the task row below.
            ExtendedFloatingActionButton(
                onClick = actions.onNewBooking,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, contentDescription = if (fabExpanded) null else stringResource(R.string.rentals_quick_book)) },
                text = { Text(stringResource(R.string.rentals_quick_book)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
                item(key = "sync", contentType = "sync") {
                    Column {
                        SyncStatus(online, lastSynced, queue.size, viewModel::syncNow)
                        RefreshFailedBanner(refresh)
                    }
                }
                if (firstLoad) {
                    item(key = "skeleton", contentType = "skeleton") { HomeSkeleton() }
                    return@LazyColumn
                }
                if (fleet.isEmpty) {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(
                            stringResource(R.string.rentals_fleet_empty),
                            icon = Icons.Default.DirectionsCar,
                            actionLabel = stringResource(R.string.rentals_fleet),
                            onAction = { actions.onOpenFleet(null) },
                        )
                    }
                } else {
                    item(key = "kpis", contentType = "kpis") { HomeKpis(fleet, actions) }
                }
                item(key = "tasks", contentType = "tasks") { PrimaryTasks(actions) }
                if (notices.isNotEmpty()) {
                    item(key = "notices", contentType = "header") { SectionHeader(stringResource(R.string.rentals_notices)) }
                    items(notices, key = { "n:${it.record.entity}:${it.record.id}" }, contentType = { "notice" }) { n ->
                        val rec = n.record
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.cardHorizontal, vertical = AvagoSpacing.cardVertical),
                        ) {
                            NoticeRow(
                                rec,
                                onOpen = (rec.orderId ?: rec.id.takeIf { rec.entity == RentalEntity.ORDER })?.let { id -> { actions.onOpenOrder(id) } },
                                onRetry = n.queueId?.let { q -> { viewModel.retry(q); Unit } },
                                onDiscard = n.queueId?.let { q -> { viewModel.discard(q); Unit } },
                            )
                        }
                    }
                }
                item(key = "today", contentType = "header") { SectionHeader(stringResource(R.string.rentals_today)) }
                val agenda = fleet.agenda
                if (agenda.overdue.isEmpty() && agenda.goingOut.isEmpty() && agenda.dueBack.isEmpty() && agenda.maintenance.isEmpty()) {
                    // One calm card instead of three empty boxes.
                    item(key = "all-clear", contentType = "agenda") { AllClearCard() }
                }
                agendaSection("overdue", R.string.rentals_today_overdue, agenda.overdue, zone, actions, HandoverTarget.IN)
                agendaSection("out", R.string.rentals_today_going_out, agenda.goingOut, zone, actions, HandoverTarget.OUT)
                agendaSection("back", R.string.rentals_today_due_back, agenda.dueBack, zone, actions, HandoverTarget.IN)
                agendaSection("maint", R.string.rentals_today_maintenance, agenda.maintenance, zone, actions, null)

                item(key = "fleet-tools", contentType = "card") {
                    SectionCard(title = stringResource(R.string.rentals_fleet_availability)) {
                        NavRow(Icons.Default.DirectionsCar, StatusTone.Accent, R.string.rentals_fleet, R.string.rentals_fleet_board_sub) { actions.onOpenFleet(null) }
                        CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                        NavRow(Icons.Default.CalendarMonth, StatusTone.Info, R.string.rentals_schedule, R.string.rentals_schedule_sub, actions.onOpenSchedule)
                        CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                        NavRow(Icons.Default.EventAvailable, StatusTone.Success, R.string.rentals_find_free, R.string.rentals_find_free_sub, actions.onFindFree)
                    }
                }
                if (operations != null) item(key = "ops", contentType = "ops") { operations() }
                item(key = "records", contentType = "card") {
                    SectionCard(title = stringResource(R.string.rentals_bookings_customers)) {
                        NavRow(Icons.Default.Receipt, StatusTone.Neutral, R.string.rentals_orders, null, actions.onOpenOrders)
                        CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                        NavRow(Icons.Default.People, StatusTone.Neutral, R.string.rentals_customers, null, actions.onOpenCustomers)
                    }
                }
            }
        }
    }
}

/** KPI tiles: the four numbers the counter checks first. Each opens the matching filtered view. */
@Composable
private fun HomeKpis(fleet: FleetSnapshot, actions: RentalsHomeActions) {
    KpiGrid(
        listOf(
            KpiItem(
                stringResource(R.string.rentals_status_on_rent), (fleet.counts[FleetStatus.ON_RENT] ?: 0).toString(),
                Icons.Default.Key, StatusTone.Accent, onClick = { actions.onOpenFleet(FleetStatus.ON_RENT) },
            ),
            KpiItem(
                stringResource(R.string.rentals_status_available), fleet.availableNow.toString(),
                Icons.Default.CheckCircle, StatusTone.Success, onClick = { actions.onOpenFleet(FleetStatus.AVAILABLE) },
            ),
            KpiItem(
                stringResource(R.string.rentals_status_overdue), (fleet.counts[FleetStatus.OVERDUE] ?: 0).toString(),
                Icons.Default.Warning,
                if ((fleet.counts[FleetStatus.OVERDUE] ?: 0) > 0) StatusTone.Danger else StatusTone.Neutral,
                onClick = { actions.onOpenFleet(FleetStatus.OVERDUE) },
            ),
            KpiItem(
                stringResource(R.string.rentals_kpi_due_today), fleet.dueToday.toString(),
                Icons.Default.Schedule, if (fleet.dueToday > 0) StatusTone.Warning else StatusTone.Neutral,
                onClick = actions.onOpenSchedule,
            ),
        ),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { SkeletonBlock(null, 104.dp, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { SkeletonBlock(null, 104.dp, Modifier.weight(1f)) }
        }
        SkeletonList(rows = 4)
    }
}

/** "Last synced" + pending count + Sync now, with an offline explanation when disconnected. */
@Composable
private fun SyncStatus(online: Boolean, lastSynced: Long?, pending: Int, onSyncNow: () -> Unit) {
    Column {
        LastSyncedRow(lastSynced, pending, onSyncNow)
        if (!online) {
            ErrorBanner(stringResource(R.string.rentals_offline_hint), tone = StatusTone.Warning, icon = Icons.Default.CloudOff)
        }
    }
}

/** The counter's two scan jobs, equal weight (Quick book is the FAB); wraps for large fonts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrimaryTasks(actions: RentalsHomeActions) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
        maxItemsInEachRow = 2,
    ) {
        FilledTonalButton(onClick = actions.onScanCheckOut, modifier = Modifier.weight(1f).heightIn(min = AvagoSpacing.rowHeight)) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.rentals_scan_checkout), Modifier.padding(start = AvagoSpacing.sm), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(onClick = actions.onScanCheckIn, modifier = Modifier.weight(1f).heightIn(min = AvagoSpacing.rowHeight)) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.rentals_check_in), Modifier.padding(start = AvagoSpacing.sm), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shown instead of empty Overdue / Going out / Due back cards on a quiet day. */
@Composable
internal fun AllClearCard() {
    SectionCard {
        ListItem(
            leadingContent = { LeadingBadge(Icons.Default.CheckCircle, StatusTone.Success) },
            headlineContent = { Text(stringResource(R.string.rentals_all_clear)) },
            supportingContent = { Text(stringResource(R.string.rentals_all_clear_body)) },
            colors = cardListItemColors(),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
    }
}

/** One non-empty "Today" group as a grouped card: title, count pill and the agenda rows. */
private fun LazyListScope.agendaSection(
    key: String,
    @StringRes title: Int,
    events: List<ScheduleEvent>,
    zone: ZoneId,
    actions: RentalsHomeActions,
    direction: String?,
) {
    if (events.isEmpty()) return
    item(key = "g:$key", contentType = "agenda") {
        val overdue = key == "overdue"
        SectionCard(
            title = stringResource(title),
            trailing = {
                StatusPill(
                    events.size.toString(),
                    if (overdue) StatusTone.Danger else StatusTone.Neutral,
                    icon = if (overdue) Icons.Default.Warning else null,
                )
            },
        ) {
            events.forEachIndexed { i, ev ->
                if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
                val orderId = ev.orderId
                // Holds are not handed over; only pickups / returns get the next-step button.
                val canHandover = orderId != null && direction != null && ev.type != "hold" && ev.type != "maintenance"
                AgendaRow(
                    ev, zone,
                    onClick = orderId?.let { id -> { actions.onOpenOrder(id) } },
                    action = if (canHandover && orderId != null && direction != null) {
                        val label = if (direction == HandoverTarget.IN) R.string.rentals_action_checkin else R.string.rentals_action_checkout
                        label to { actions.onHandover(orderId, direction) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, tone: StatusTone, @StringRes title: Int, @StringRes sub: Int?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = sub?.let { { Text(stringResource(it)) } },
        leadingContent = { LeadingBadge(icon, tone) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = cardListItemColors(),
        modifier = Modifier
            .heightIn(min = MinTouch)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * Fleet summary card for the app home / asset list header (§11.1): status counts that open the
 * fleet screen pre-filtered. Hidden while the account has no rentable units.
 */
@Composable
fun RentalsFleetSummaryCard(
    onOpenFleet: (FleetStatus?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RentalsOverviewViewModel = hiltViewModel(),
) {
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    if (fleet.isEmpty) return
    FleetSummaryContent(fleet, onOpenFleet, modifier)
}

/**
 * Fleet status breakdown: one chip (icon + label + count) per status. [compact] drops the
 * "available now" headline when KPI tiles above already show it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FleetSummaryContent(
    fleet: FleetSnapshot,
    onOpenFleet: (FleetStatus?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.rentals_fleet_summary, fleet.units.size),
        trailing = {
            TextButton(onClick = { onOpenFleet(null) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.rentals_view_all))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!compact) {
                // The number the counter asks for first: what can go out right now.
                TextButton(
                    onClick = { onOpenFleet(FleetStatus.AVAILABLE) },
                    modifier = Modifier.heightIn(min = MinTouch),
                ) {
                    Text(
                        stringResource(R.string.rentals_available_now, fleet.availableNow),
                        style = MaterialTheme.typography.headlineMedium,
                        color = toneColors(StatusTone.Success).content,
                    )
                }
                if (fleet.dueToday > 0) {
                    Text(
                        stringResource(R.string.rentals_due_today_count, fleet.dueToday),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FleetStatus.entries.forEach { s ->
                    val n = fleet.counts[s] ?: 0
                    val label = "${stringResource(fleetStatusLabel(s))} $n"
                    val c = toneColors(fleetStatusTone(s))
                    FilterChip(
                        selected = false,
                        onClick = { onOpenFleet(s) },
                        label = { Text(label) },
                        leadingIcon = { Icon(fleetStatusIcon(s), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = c.container,
                            labelColor = c.content,
                            iconColor = c.content,
                        ),
                        border = null,
                        modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = label },
                    )
                }
            }
        }
    }
}

@Composable
internal fun NoticeRow(
    rec: RentalRecordEntity,
    onOpen: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onDiscard: (() -> Unit)?,
) {
    val title = rec.name ?: rec.status ?: rec.entity.removePrefix("rental_").replace('_', ' ')
    val conflict = rec.localState == RentalRecordEntity.STATE_CONFLICT
    val failed = isSyncFailed(rec.notice)
    ListItem(
        modifier = if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier,
        headlineContent = { Text(title) },
        leadingContent = {
            LeadingBadge(Icons.Default.Warning, if (conflict || failed) StatusTone.Danger else StatusTone.Warning)
        },
        supportingContent = {
            Column {
                Text(
                    noticeText(rec.notice, conflict),
                    color = if (conflict) toneColors(StatusTone.Danger).content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onRetry != null || onDiscard != null) {
                    Row {
                        onRetry?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_retry)) } }
                        onDiscard?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.rentals_discard)) } }
                    }
                }
            }
        },
        trailingContent = { SyncStateBadge(rec.localState, failed = failed) },
        colors = cardListItemColors(),
    )
}

private fun eventTone(ev: ScheduleEvent): StatusTone = when {
    ev.overdue -> StatusTone.Danger
    ev.type == "pickup" -> StatusTone.Info
    ev.type == "return" -> StatusTone.Success
    ev.type == "hold" || ev.type == "maintenance" -> StatusTone.Warning
    else -> StatusTone.Neutral
}

private fun eventIcon(type: String): ImageVector = when (type) {
    "pickup" -> Icons.AutoMirrored.Filled.Logout
    "return" -> Icons.AutoMirrored.Filled.Login
    "hold" -> Icons.Default.PauseCircle
    "maintenance" -> Icons.Default.Build
    else -> Icons.Default.CalendarMonth
}

/**
 * Agenda line (time · type, unit, order / customer). [action] adds the next step for the line
 * (check out / check in) as a button and a swipe-right gesture (also exposed to TalkBack as a
 * custom action); otherwise overdue lines carry the overdue chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendaRow(
    ev: ScheduleEvent,
    zone: ZoneId,
    onClick: (() -> Unit)?,
    action: Pair<Int, () -> Unit>? = null,
) {
    val type = stringResource(eventLabel(ev.type))
    val actionLabel = action?.let { stringResource(it.first) }
    val row: @Composable () -> Unit = {
        ListItem(
            modifier = Modifier
                .heightIn(min = MinTouch)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .then(
                    if (action != null && actionLabel != null) {
                        Modifier.semantics {
                            customActions = listOf(CustomAccessibilityAction(actionLabel) { action.second(); true })
                        }
                    } else {
                        Modifier
                    },
                ),
            leadingContent = { UnitThumbnail(ev.assetId, eventIcon(ev.type), eventTone(ev)) },
            headlineContent = {
                Text(
                    ev.assetName ?: ev.assetId ?: stringResource(R.string.rentals_unassigned_unit),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                // time · kind · order · customer — one scannable line under the unit.
                val parts = listOfNotNull(formatTime(ev.at, zone), type, ev.orderNumber, ev.customerName)
                Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                when {
                    action != null && actionLabel != null -> FilledTonalButton(onClick = action.second, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(actionLabel)
                    }
                    ev.overdue -> FleetStatusChip(FleetStatus.OVERDUE)
                }
            },
            colors = cardListItemColors(),
        )
    }
    if (action == null || actionLabel == null) {
        row()
        return
    }
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.tick()
                action.second()
            }
            false // reveal-and-act; the row stays in place
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val c = toneColors(if (ev.type == "return" || ev.overdue) StatusTone.Success else StatusTone.Info)
            Box(Modifier.fillMaxSize().background(c.container).padding(horizontal = AvagoSpacing.xl), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                    Icon(eventIcon(ev.type), contentDescription = null, tint = c.content)
                    Text(actionLabel, color = c.content, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { row() }
}
