package com.avago.feature.rentals.ui

import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.components.isDarkScheme
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.SkeletonListItem
import com.avago.core.ui.components.StickyDayHeader
import com.avago.feature.rentals.domain.Availability
import com.avago.feature.rentals.domain.LocalSchedule
import com.avago.feature.rentals.R

/**
 * Schedule (§11.1): range chips, sticky day headers over the agenda; a Gantt board is added on
 * tablets. Offline (Room) with pull-to-refresh for a background sync.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RentalScheduleScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    viewModel: RentalsOverviewViewModel = hiltViewModel(),
) {
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    val lastSynced by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(7) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val from = today.atStartOfDay(zone).toInstant()
    val to = today.plusDays(days.toLong()).atStartOfDay(zone).toInstant()
    val events = remember(fleet, days) { LocalSchedule.events(fleet.records, from, to, fleet.now) }
    val byDay = remember(events) { events.groupBy { it.at.atZone(zone).toLocalDate() }.toSortedMap() }
    val busy = remember(fleet) { LocalSchedule.busy(fleet.records) }
    val wide = isWide()
    val refresh = rememberSyncRefresh(lastSynced, viewModel::syncNow)
    val firstLoad = rememberFirstLoad(fleet.isEmpty, lastSynced)

    Scaffold(topBar = { RentalsTopBar(stringResource(R.string.rentals_schedule), onBack) }) { pad ->
        PullToRefreshBox(
            isRefreshing = refresh.refreshing,
            onRefresh = refresh.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 32.dp)) {
                item(key = "sync", contentType = "sync") { LastSyncedRow(lastSynced, queue.size, viewModel::syncNow) }
                item(key = "range", contentType = "filters") {
                    ChipRow {
                        listOf(1, 7, 14).forEach { n ->
                            FilterChip(
                                selected = days == n,
                                onClick = { days = n },
                                label = { Text(stringResource(R.string.rentals_days_n, n)) },
                                leadingIcon = if (days == n) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                                modifier = Modifier.heightIn(min = MinTouch),
                            )
                        }
                    }
                }
                if (wide && fleet.units.isNotEmpty()) {
                    item(key = "gantt", contentType = "gantt") {
                        SectionCard(title = stringResource(R.string.rentals_gantt)) {
                            GanttBoard(
                                units = fleet.units.map { it.unit.assetId to it.unit.name },
                                busy = busy,
                                from = from,
                                to = to,
                                now = fleet.now,
                                zone = zone,
                            )
                        }
                    }
                }
                when {
                    firstLoad -> items(5, key = { "sk$it" }, contentType = { "skeleton" }) { SkeletonListItem() }
                    byDay.isEmpty() -> item(key = "empty", contentType = "empty") {
                        EmptyState(stringResource(R.string.rentals_schedule_empty), icon = Icons.Default.EventAvailable)
                    }
                }
                byDay.forEach { (day, evs) ->
                    stickyHeader(key = "d:$day", contentType = "header") {
                        StickyDayHeader(dayLabel(day, zone), count = evs.size)
                    }
                    items(evs, key = { "e:${it.type}:${it.allocationId}:${it.at}" }, contentType = { "event" }) { ev ->
                        Column(Modifier.animateItem()) {
                            AgendaRow(ev, zone, onClick = ev.orderId?.let { id -> { onOpenOrder(id) } })
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
/**
 * Horizontal booking bars per unit (chart style guide: categorical palette, value-axis grid
 * only, muted 11sp labels, no borders). Colour is never the only cue: the agenda below lists
 * every event, and the canvas exposes a summary to TalkBack.
 */
@Composable
private fun GanttBoard(
    units: List<Pair<String, String>>,
    busy: List<Availability.Busy>,
    from: Instant,
    to: Instant,
    now: Instant,
    zone: ZoneId,
) {
    val dark = isDarkScheme()
    val blue = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val violet = if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    val slate = if (dark) Color(0xFF94A3B8) else Color(0xFF475569)
    val negative = if (dark) Color(0xFFF87171) else Color(0xFFB91C1C)
    val grid = if (dark) Color(0x1ACDD9E5) else Color(0x141F2328)
    val muted = if (dark) Color(0xFFADBAC7) else Color(0xFF57606A)
    val measurer = rememberTextMeasurer()
    val rowH = 32.dp
    val labelW = 140.dp
    val totalDays = Duration.between(from, to).toDays().coerceAtLeast(1)
    val dayW = 96.dp
    val summary = stringResource(R.string.rentals_gantt_summary, units.size, busy.count { it.end == null || it.end.isAfter(from) })
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = summary },
    ) {
        // Day/month column header in the user's order and separators (en-US "5/14", de-DE "14.5.").
        val locale = LocalConfiguration.current.locales[0]
        val dayFormatter = remember(locale) {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"), locale)
        }
        Canvas(Modifier.width(labelW + dayW * totalDays.toInt()).height(rowH * (units.size + 1))) {
            val lw = labelW.toPx()
            val dw = dayW.toPx()
            val rh = rowH.toPx()
            val spanMs = (to.toEpochMilli() - from.toEpochMilli()).toFloat()
            fun x(t: Instant) = lw + ((t.toEpochMilli() - from.toEpochMilli()).coerceIn(0, spanMs.toLong()) / spanMs) * dw * totalDays
            val style = TextStyle(color = muted, fontSize = 11.sp)
            for (d in 0 until totalDays) {
                val gx = lw + d * dw
                drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1f)
                val label = from.atZone(zone).plusDays(d).toLocalDate().format(dayFormatter)
                drawText(measurer, label, Offset(gx + 4f, 4f), style)
            }
            units.forEachIndexed { i, (id, name) ->
                val y = rh * (i + 1)
                drawText(measurer, name.take(18), Offset(0f, y + rh / 4), style)
                busy.filter { it.assetId == id && it.blocking }.forEach { b ->
                    val end = b.end ?: to
                    if (!end.isAfter(from) || !b.start.isBefore(to)) return@forEach
                    val color = when {
                        b.kind == "on_rent" && end.isBefore(now) -> negative
                        b.kind == "on_rent" -> violet
                        b.kind == "maintenance" || b.kind == "buffer" -> slate
                        else -> blue
                    }
                    val x0 = x(b.start)
                    val x1 = maxOf(x(if (b.kind == "on_rent" && end.isBefore(now)) now else end), x0 + 4f)
                    drawRoundRect(color, Offset(x0, y + rh * 0.2f), Size(x1 - x0, rh * 0.6f), CornerRadius(4f, 4f))
                }
            }
            val nx = x(now)
            if (now.isAfter(from) && now.isBefore(to)) drawLine(negative, Offset(nx, 0f), Offset(nx, size.height), 2f)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.rentals_gantt_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
