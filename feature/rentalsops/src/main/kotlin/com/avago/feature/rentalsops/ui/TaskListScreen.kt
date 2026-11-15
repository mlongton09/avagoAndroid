package com.avago.feature.rentalsops.ui

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SkeletonList
import com.avago.core.ui.components.StickyDayHeader
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.TaskView
import com.avago.feature.rentalsops.domain.TaskKind
import com.avago.feature.rentalsops.domain.TaskStatus
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

enum class TaskFilter { OPEN, DONE }

data class TaskListState(
    val tasks: List<TaskView> = emptyList(),
    val filter: TaskFilter = TaskFilter.OPEN,
    val mineOnly: Boolean = true,
    val canSeeAll: Boolean = false,
    /** `rental.drive`: the G5 route view is available. */
    val canRoute: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class TaskListViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    private val filter = MutableStateFlow(TaskFilter.OPEN)
    private val mineOnly = MutableStateFlow(true)

    val state: StateFlow<TaskListState> = combine(repo.observeTasks(), filter, mineOnly, repo.access) { all, f, mine, access ->
        val me = repo.userId()
        val canSeeAll = access.dispatch || access.statements
        val visible = all
            .filter { !mine || !canSeeAll || it.task.driverUserId == me }
            .filter { if (f == TaskFilter.OPEN) it.localStatus.isOpen else !it.localStatus.isOpen }
            .sortedWith(
                compareBy<TaskView> { it.task.scheduledAt ?: Instant.MAX }
                    .thenBy { it.task.routeSeq ?: Int.MAX_VALUE },
            )
            .let { if (f == TaskFilter.DONE) it.reversed() else it }
        TaskListState(visible, f, mine, canSeeAll, canRoute = access.drive, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    fun setFilter(f: TaskFilter) { filter.value = f }
    fun setMineOnly(v: Boolean) { mineOnly.value = v }
    fun sync() = repo.requestSync()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, vm: TaskListViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        // Sync runs in the background worker; the indicator only acknowledges the gesture.
        if (refreshing) { delay(1_200); refreshing = false }
    }
    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(s.tasks) { s.tasks.groupBy { t -> t.task.scheduledAt?.atZone(zone)?.toLocalDate() } }
    Scaffold(
        topBar = {
            OpsTopBar(stringResource(R.string.rentalops_tasks_title), onBack) {
                if (s.canRoute) {
                    IconButton(onClick = { onNavigate(RentalOpsRoutes.ROUTE) }) {
                        Icon(Icons.Filled.Route, contentDescription = stringResource(R.string.rentalops_route_title))
                    }
                }
                IconButton(onClick = vm::sync) { Icon(Icons.Filled.Sync, contentDescription = stringResource(R.string.rentalops_sync_now)) }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm),
            ) {
                FilterChip(
                    selected = s.filter == TaskFilter.OPEN,
                    onClick = { vm.setFilter(TaskFilter.OPEN) },
                    label = { Text(stringResource(R.string.rentalops_filter_open)) },
                    leadingIcon = { Icon(Icons.Filled.Pending, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
                FilterChip(
                    selected = s.filter == TaskFilter.DONE,
                    onClick = { vm.setFilter(TaskFilter.DONE) },
                    label = { Text(stringResource(R.string.rentalops_filter_done)) },
                    leadingIcon = { Icon(Icons.Filled.TaskAlt, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
                if (s.canSeeAll) {
                    FilterChip(
                        selected = s.mineOnly,
                        onClick = { vm.setMineOnly(!s.mineOnly) },
                        label = { Text(stringResource(R.string.rentalops_filter_mine)) },
                        leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(18.dp)) },
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; vm.sync() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !s.loaded -> SkeletonList(rows = 5)
                    s.tasks.isEmpty() -> EmptyState(
                        stringResource(R.string.rentalops_tasks_empty),
                        Modifier.verticalScroll(rememberScrollState()),
                        icon = Icons.Filled.LocalShipping,
                        actionLabel = if (s.canSeeAll && s.mineOnly) stringResource(R.string.rentalops_show_all) else null,
                        onAction = if (s.canSeeAll && s.mineOnly) { { vm.setMineOnly(false) } } else null,
                    )
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = AvagoSpacing.xxl)) {
                        groups.forEach { (day, tasks) ->
                            stickyHeader(key = "h-${day ?: "none"}", contentType = "header") {
                                StickyDayHeader(day?.let { opsDayLabel(it, zone) } ?: stringResource(R.string.rentalops_unscheduled), count = tasks.size)
                            }
                            items(tasks, key = { it.task.id }, contentType = { "task" }) { v ->
                                TaskRow(v, Modifier.animateItem()) { onNavigate(RentalOpsRoutes.task(v.task.id)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(v: TaskView, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = v.task
    val status = statusLabel(v.localStatus)
    val headline = t.customerName ?: t.orderNumber?.let { stringResource(R.string.rentalops_order_n, it) } ?: kindLabel(t.kind)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.xs)
            .semantics { stateDescription = status },
    ) {
        Row(Modifier.padding(AvagoSpacing.md), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.md)) {
            LeadingBadge(kindIcon(t.kind), taskStatusTone(v.localStatus))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(headline, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(kindLabel(t.kind), t.orderNumber?.takeIf { t.customerName != null }?.let { stringResource(R.string.rentalops_order_n, it) })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    t.scheduledAt?.let { Text(Fmt.time(it), style = MaterialTheme.typography.titleSmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.xs), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Place, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        t.jobSiteName?.let { site -> listOfNotNull(site, t.address).joinToString(", ") } ?: t.address ?: stringResource(R.string.rentalops_no_address),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm), verticalArrangement = Arrangement.spacedBy(AvagoSpacing.xs)) {
                    TaskStatusPill(v.localStatus)
                    PendingChip(v.pending)
                }
            }
        }
    }
}

/** "Today" / "Tomorrow" / "Yesterday" or the localised weekday + date for a sticky header. */
@Composable
internal fun opsDayLabel(day: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    return when (day) {
        today -> stringResource(R.string.rentalops_today)
        today.plusDays(1) -> stringResource(R.string.rentalops_tomorrow)
        today.minusDays(1) -> stringResource(R.string.rentalops_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
}

@Composable
internal fun kindLabel(k: TaskKind): String = stringResource(
    when (k) {
        TaskKind.DELIVERY -> R.string.rentalops_kind_delivery
        TaskKind.PICKUP -> R.string.rentalops_kind_pickup
        TaskKind.TRANSFER -> R.string.rentalops_kind_transfer_task
    },
)

internal fun kindIcon(k: TaskKind) = when (k) {
    TaskKind.DELIVERY -> Icons.Filled.LocalShipping
    TaskKind.PICKUP -> Icons.AutoMirrored.Filled.Undo
    TaskKind.TRANSFER -> Icons.AutoMirrored.Filled.CompareArrows
}

@Composable
internal fun statusLabel(s: TaskStatus): String = stringResource(
    when (s) {
        TaskStatus.PENDING -> R.string.rentalops_status_pending
        TaskStatus.ASSIGNED -> R.string.rentalops_status_assigned
        TaskStatus.EN_ROUTE -> R.string.rentalops_status_en_route
        TaskStatus.ARRIVED -> R.string.rentalops_status_arrived
        TaskStatus.COMPLETED -> R.string.rentalops_status_completed
        TaskStatus.FAILED -> R.string.rentalops_status_failed
        TaskStatus.CANCELLED -> R.string.rentalops_status_cancelled
    },
)
