package com.avago.feature.rentals.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.FleetStatus
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
 * Rentals landing screen (spec §14), task-first: the three counter jobs as big buttons, sync /
 * offline state, the fleet at a glance ("available now"), today's work split into overdue /
 * going out / due back, then fleet & availability tools, operations (slot, permission-gated
 * by the host) and records.
 *
 * [onBack] is null when the screen is a top-level destination: the host app bar (menu + title)
 * is shown instead of this screen's own bar.
 */
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

    Scaffold(
        topBar = { if (onBack != null) RentalsTopBar(stringResource(R.string.rentals_title), onBack) },
        // As a top-level tab the host Scaffold already applies the system-bar insets.
        contentWindowInsets = if (onBack == null) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            item(key = "sync") { SyncStatus(online, lastSynced, queue.size, viewModel::syncNow) }
            item(key = "tasks") { PrimaryTasks(actions) }
            item(key = "fleet") {
                FleetSummaryContent(fleet, actions.onOpenFleet, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (notices.isNotEmpty()) {
                item(key = "notices") { SectionHeader(stringResource(R.string.rentals_notices)) }
                items(notices, key = { "n:${it.record.entity}:${it.record.id}" }) { n ->
                    val rec = n.record
                    NoticeRow(
                        rec,
                        onOpen = (rec.orderId ?: rec.id.takeIf { rec.entity == RentalEntity.ORDER })?.let { id -> { actions.onOpenOrder(id) } },
                        onRetry = n.queueId?.let { q -> { viewModel.retry(q); Unit } },
                        onDiscard = n.queueId?.let { q -> { viewModel.discard(q); Unit } },
                    )
                }
            }
            item(key = "today") { SectionHeader(stringResource(R.string.rentals_today)) }
            agendaSection(
                "overdue", R.string.rentals_today_overdue, R.string.rentals_today_overdue_empty, fleet.agenda.overdue, zone, actions,
                HandoverTarget.IN,
            )
            agendaSection(
                "out", R.string.rentals_today_going_out, R.string.rentals_today_going_out_empty, fleet.agenda.goingOut, zone, actions,
                HandoverTarget.OUT,
            )
            agendaSection(
                "back", R.string.rentals_today_due_back, R.string.rentals_today_due_back_empty, fleet.agenda.dueBack, zone, actions,
                HandoverTarget.IN,
            )
            if (fleet.agenda.maintenance.isNotEmpty()) {
                agendaSection("maint", R.string.rentals_today_maintenance, null, fleet.agenda.maintenance, zone, actions, null)
            }

            item(key = "fleet-tools") {
                Column {
                    SectionHeader(stringResource(R.string.rentals_fleet_availability))
                    NavRow(Icons.Default.DirectionsCar, R.string.rentals_fleet, R.string.rentals_fleet_board_sub) { actions.onOpenFleet(null) }
                    NavRow(Icons.Default.CalendarMonth, R.string.rentals_schedule, R.string.rentals_schedule_sub, actions.onOpenSchedule)
                    NavRow(Icons.Default.EventAvailable, R.string.rentals_find_free, R.string.rentals_find_free_sub, actions.onFindFree)
                }
            }
            if (operations != null) item(key = "ops") { operations() }
            item(key = "records") {
                Column {
                    SectionHeader(stringResource(R.string.rentals_bookings_customers))
                    NavRow(Icons.Default.Receipt, R.string.rentals_orders, null, actions.onOpenOrders)
                    NavRow(Icons.Default.People, R.string.rentals_customers, null, actions.onOpenCustomers)
                }
            }
        }
    }
}

/** "Last synced" + pending count + Sync now, with an offline explanation when disconnected. */
@Composable
private fun SyncStatus(online: Boolean, lastSynced: Long?, pending: Int, onSyncNow: () -> Unit) {
    Column {
        LastSyncedRow(lastSynced, pending, onSyncNow)
        if (!online) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.rentals_offline_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The three counter jobs as large, labelled buttons (icon above text, wraps for large fonts). */
@Composable
private fun PrimaryTasks(actions: RentalsHomeActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val big = Modifier
            .weight(1f)
            .heightIn(min = 88.dp)
        Button(onClick = actions.onNewBooking, modifier = big, contentPadding = PaddingValues(8.dp)) {
            TaskLabel(Icons.Default.Add, R.string.rentals_new_booking)
        }
        FilledTonalButton(onClick = actions.onScanCheckOut, modifier = big, contentPadding = PaddingValues(8.dp)) {
            TaskLabel(Icons.Default.QrCodeScanner, R.string.rentals_scan_checkout)
        }
        FilledTonalButton(onClick = actions.onScanCheckIn, modifier = big, contentPadding = PaddingValues(8.dp)) {
            TaskLabel(Icons.AutoMirrored.Filled.Login, R.string.rentals_check_in)
        }
    }
}

@Composable
private fun TaskLabel(icon: ImageVector, @StringRes label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
}

/** One "Today" list with its own heading and empty-state guidance. */
private fun LazyListScope.agendaSection(
    key: String,
    @StringRes title: Int,
    @StringRes empty: Int?,
    events: List<ScheduleEvent>,
    zone: ZoneId,
    actions: RentalsHomeActions,
    direction: String?,
) {
    item(key = "h:$key") {
        Text(
            "${stringResource(title)} · ${events.size}",
            style = MaterialTheme.typography.labelLarge,
            color = if (key == "overdue" && events.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
    if (events.isEmpty()) {
        if (empty != null) {
            item(key = "e:$key") {
                Text(
                    stringResource(empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        return
    }
    items(events, key = { "t:$key:${it.type}:${it.allocationId}:${it.at}" }) { ev ->
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
        HorizontalDivider()
    }
}

@Composable
private fun NavRow(icon: ImageVector, @StringRes title: Int, @StringRes sub: Int?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = sub?.let { { Text(stringResource(it)) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FleetSummaryContent(fleet: FleetSnapshot, onOpenFleet: (FleetStatus?) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.rentals_fleet_summary, fleet.units.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenFleet(null) }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.rentals_view_all))
                }
            }
            // The number the counter asks for first: what can go out right now.
            TextButton(
                onClick = { onOpenFleet(FleetStatus.AVAILABLE) },
                modifier = Modifier.heightIn(min = MinTouch),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(
                    stringResource(R.string.rentals_available_now, fleet.availableNow),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (fleet.dueToday > 0) {
                Text(
                    stringResource(R.string.rentals_due_today_count, fleet.dueToday),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FleetStatus.entries.forEach { s ->
                    val n = fleet.counts[s] ?: 0
                    val label = "${stringResource(fleetStatusLabel(s))} $n"
                    val (bg, fg) = fleetStatusColors(s)
                    AssistChip(
                        onClick = { onOpenFleet(s) },
                        label = { Text(label, color = fg) },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = bg),
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
        supportingContent = {
            Column {
                Text(
                    noticeText(rec.notice, conflict),
                    color = if (conflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
    )
}

/**
 * Agenda line (time · type, unit, order / customer). [action] adds the next step for the line
 * (check out / check in) as a button; otherwise overdue lines carry the overdue chip.
 */
@Composable
internal fun AgendaRow(
    ev: ScheduleEvent,
    zone: ZoneId,
    onClick: (() -> Unit)?,
    action: Pair<Int, () -> Unit>? = null,
) {
    val type = stringResource(eventLabel(ev.type))
    ListItem(
        modifier = Modifier
            .heightIn(min = MinTouch)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        overlineContent = { Text("${formatTime(ev.at, zone)} · $type") },
        headlineContent = { Text(ev.assetName ?: ev.assetId ?: "—") },
        supportingContent = {
            val parts = listOfNotNull(ev.orderNumber, ev.customerName)
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
        },
        trailingContent = {
            when {
                action != null -> TextButton(onClick = action.second, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(action.first))
                }
                ev.overdue -> FleetStatusChip(FleetStatus.OVERDUE)
            }
        },
    )
}
