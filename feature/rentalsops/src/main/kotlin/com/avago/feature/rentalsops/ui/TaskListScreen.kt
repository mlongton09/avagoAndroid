package com.avago.feature.rentalsops.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.data.TaskView
import com.avago.feature.rentalsops.domain.TaskKind
import com.avago.feature.rentalsops.domain.TaskStatus
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

enum class TaskFilter { OPEN, DONE }

data class TaskListState(
    val tasks: List<TaskView> = emptyList(),
    val filter: TaskFilter = TaskFilter.OPEN,
    val mineOnly: Boolean = true,
    val canSeeAll: Boolean = false,
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
        TaskListState(visible, f, mine, canSeeAll, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    fun setFilter(f: TaskFilter) { filter.value = f }
    fun setMineOnly(v: Boolean) { mineOnly.value = v }
    fun sync() = repo.requestSync()
}

@Composable
fun TaskListScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, vm: TaskListViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { OpsTopBar(stringResource(R.string.rentalops_tasks_title), onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AvagoSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AvagoSpacing.sm)) {
                FilterChip(
                    selected = s.filter == TaskFilter.OPEN,
                    onClick = { vm.setFilter(TaskFilter.OPEN) },
                    label = { Text(stringResource(R.string.rentalops_filter_open)) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
                FilterChip(
                    selected = s.filter == TaskFilter.DONE,
                    onClick = { vm.setFilter(TaskFilter.DONE) },
                    label = { Text(stringResource(R.string.rentalops_filter_done)) },
                    modifier = Modifier.heightIn(min = MinTouch),
                )
                if (s.canSeeAll) {
                    FilterChip(
                        selected = s.mineOnly,
                        onClick = { vm.setMineOnly(!s.mineOnly) },
                        label = { Text(stringResource(R.string.rentalops_filter_mine)) },
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
            }
            if (s.loaded && s.tasks.isEmpty()) {
                EmptyState(stringResource(R.string.rentalops_tasks_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(s.tasks, key = { it.task.id }) { v -> TaskRow(v) { onNavigate(RentalOpsRoutes.task(v.task.id)) } }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(v: TaskView, onClick: () -> Unit) {
    val t = v.task
    val status = statusLabel(v.localStatus)
    ListItem(
        headlineContent = {
            Text(listOfNotNull(kindLabel(t.kind), t.customerName ?: t.orderNumber?.let { stringResource(R.string.rentalops_order_n, it) }).joinToString(" · "))
        },
        supportingContent = {
            Column {
                // Order number as a second line when the headline already shows the customer.
                if (t.customerName != null) t.orderNumber?.let { Text(stringResource(R.string.rentalops_order_n, it), style = MaterialTheme.typography.bodySmall) }
                Text(t.jobSiteName?.let { site -> listOfNotNull(site, t.address).joinToString(", ") } ?: t.address ?: stringResource(R.string.rentalops_no_address), maxLines = 2)
                Text(Fmt.dateTime(t.scheduledAt), style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = { Icon(kindIcon(t.kind), contentDescription = null) },
        trailingContent = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor(v.localStatus))
                PendingChip(v.pending)
            }
        },
        modifier = Modifier
            .heightIn(min = MinTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = status },
    )
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

@Composable
internal fun statusColor(s: TaskStatus) = when (s) {
    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    TaskStatus.EN_ROUTE, TaskStatus.ARRIVED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
