package com.avago.feature.rentals.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.FleetStatus
import com.avago.feature.rentals.R
import com.avago.feature.rentals.domain.ScheduleEvent
import java.time.ZoneId

/** Rentals landing screen (spec §14): fleet summary, today, notices, quick actions. */
@Composable
fun RentalsHomeScreen(
    onBack: (() -> Unit)?,
    onOpenFleet: (FleetStatus?) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenCustomers: () -> Unit,
    onQuickBook: () -> Unit,
    onScan: () -> Unit,
    viewModel: RentalsOverviewViewModel = hiltViewModel(),
) {
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.rentals_title), onBack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 96.dp)) {
            item { LastSyncedRow(lastSynced, queue.size, viewModel::syncNow) }
            item {
                FleetSummaryContent(fleet, onOpenFleet, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item {
                QuickActions(
                    listOf(
                        Triple(R.string.rentals_quick_book, Icons.Default.Add, onQuickBook),
                        Triple(R.string.rentals_scan, Icons.Default.QrCodeScanner, onScan),
                        Triple(R.string.rentals_fleet, Icons.Default.DirectionsCar) { onOpenFleet(null) },
                        Triple(R.string.rentals_schedule, Icons.Default.CalendarMonth, onOpenSchedule),
                        Triple(R.string.rentals_orders, Icons.Default.Receipt, onOpenOrders),
                        Triple(R.string.rentals_customers, Icons.Default.People, onOpenCustomers),
                    ),
                )
            }
            if (notices.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.rentals_notices)) }
                items(notices, key = { "n:${it.entity}:${it.id}" }) { rec ->
                    NoticeRow(
                        rec,
                        onOpen = { (rec.orderId ?: rec.id.takeIf { rec.entity == com.avago.feature.rentals.domain.RentalEntity.ORDER })?.let(onOpenOrder) },
                        onRetry = viewModel.queueFor(rec)?.let { q -> { viewModel.retry(q.queueId); Unit } },
                        onDiscard = viewModel.queueFor(rec)?.let { q -> { viewModel.discard(q.queueId); Unit } },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.rentals_today)) }
            if (fleet.today.isEmpty()) {
                item { EmptyState(stringResource(R.string.rentals_today_empty)) }
            } else {
                items(fleet.today, key = { "t:${it.type}:${it.allocationId}:${it.at}" }) { ev ->
                    AgendaRow(ev, zone, onClick = ev.orderId?.let { id -> { onOpenOrder(id) } })
                    HorizontalDivider()
                }
            }
        }
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(actions: List<Triple<Int, ImageVector, () -> Unit>>) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { (label, icon, onClick) ->
            OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(icon, contentDescription = null)
                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
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

@Composable
internal fun AgendaRow(ev: ScheduleEvent, zone: ZoneId, onClick: (() -> Unit)?) {
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
            if (ev.overdue) FleetStatusChip(FleetStatus.OVERDUE)
        },
    )
}
