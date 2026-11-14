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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.PendingAction
import com.avago.feature.rentalsops.data.RentalOpsAccess
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.domain.WidgetCounts
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeState(
    val access: RentalOpsAccess = RentalOpsAccess.NONE,
    val counts: WidgetCounts.Counts = WidgetCounts.Counts.EMPTY,
    val queue: List<PendingAction> = emptyList(),
) {
    val waiting: Int get() = queue.count { !it.rejected && !it.parked }
    val rejected: Int get() = queue.count { it.rejected || it.parked }
}

@HiltViewModel
class RentalOpsHomeViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repo.access, repo.observeCounts(), repo.observeQueue()) { a, c, q -> HomeState(a, c, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun sync() = repo.requestSync()
}

@Composable
fun RentalOpsHomeScreen(onNavigate: (String) -> Unit, onBack: (() -> Unit)?, vm: RentalOpsHomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            OpsTopBar(stringResource(R.string.rentalops_title), onBack) {
                IconButton(onClick = vm::sync) { Icon(Icons.Filled.Sync, contentDescription = stringResource(R.string.rentalops_sync_now)) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            if (s.access.statements || s.access.tasks) {
                item { CountsRow(s.counts) }
            }
            if (s.waiting > 0 || s.rejected > 0) {
                item {
                    Text(
                        stringResource(R.string.rentalops_queue_summary, s.waiting, s.rejected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.rejected > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AvagoSpacing.lg, vertical = AvagoSpacing.sm),
                    )
                }
            }
            if (!s.access.any) item { EmptyState(stringResource(R.string.rentalops_no_access)) }
            if (s.access.tasks) item { Entry(Icons.Filled.LocalShipping, R.string.rentalops_tasks_title, R.string.rentalops_tasks_sub) { onNavigate(RentalOpsRoutes.TASKS) } }
            if (s.access.assign) item { Entry(Icons.Filled.Inventory2, R.string.rentalops_assign_title, R.string.rentalops_assign_sub) { onNavigate(RentalOpsRoutes.ASSIGN) } }
            if (s.access.transfers) item { Entry(Icons.AutoMirrored.Filled.CompareArrows, R.string.rentalops_transfers_title, R.string.rentalops_transfers_sub) { onNavigate(RentalOpsRoutes.TRANSFERS) } }
            if (s.access.statements) item { Entry(Icons.AutoMirrored.Filled.ReceiptLong, R.string.rentalops_statement_title, R.string.rentalops_statement_sub) { onNavigate(RentalOpsRoutes.STATEMENT) } }
            if (s.access.aging) item { Entry(Icons.Filled.BarChart, R.string.rentalops_aging_title, R.string.rentalops_aging_sub) { onNavigate(RentalOpsRoutes.AGING) } }
            if (s.access.any) item { Entry(Icons.Filled.NotificationsActive, R.string.rentalops_settings_title, R.string.rentalops_settings_sub) { onNavigate(RentalOpsRoutes.SETTINGS) } }
        }
    }
}

@Composable
private fun CountsRow(c: WidgetCounts.Counts) {
    val a11y = stringResource(R.string.rentalops_widget_a11y, c.out, c.dueToday, c.overdue)
    Card(
        Modifier
            .fillMaxWidth()
            .padding(AvagoSpacing.lg)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Row(Modifier.fillMaxWidth().padding(AvagoSpacing.lg), horizontalArrangement = Arrangement.SpaceEvenly) {
            Count(c.out, stringResource(R.string.rentalops_widget_out), MaterialTheme.colorScheme.onSurface)
            Count(c.dueToday, stringResource(R.string.rentalops_widget_due_today), MaterialTheme.colorScheme.onSurface)
            Count(c.overdue, stringResource(R.string.rentalops_widget_overdue), if (c.overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Entry(icon: ImageVector, title: Int, sub: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(sub)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .heightIn(min = MinTouch)
            .clickable(onClickLabel = stringResource(R.string.rentalops_open), role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    )
}
