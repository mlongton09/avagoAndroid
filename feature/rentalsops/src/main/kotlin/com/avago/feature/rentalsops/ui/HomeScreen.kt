package com.avago.feature.rentalsops.ui

import javax.inject.Inject
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.design.theme.AvagoSpacing
import com.avago.core.ui.components.CardDivider
import com.avago.core.ui.components.cardListItemColors
import com.avago.core.ui.components.ErrorBanner
import com.avago.core.ui.components.KpiGrid
import com.avago.core.ui.components.KpiItem
import com.avago.core.ui.components.LeadingBadge
import com.avago.core.ui.components.SectionCard
import com.avago.core.ui.components.StatusTone
import com.avago.feature.rentalsops.data.QueueSummary
import com.avago.feature.rentalsops.data.RentalOpsAccess
import com.avago.feature.rentalsops.data.RentalOpsRepository
import com.avago.feature.rentalsops.domain.OpsCounts
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import com.avago.feature.rentalsops.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val access: RentalOpsAccess = RentalOpsAccess.NONE,
    val counts: OpsCounts = OpsCounts.EMPTY,
    val queue: QueueSummary = QueueSummary(0, 0),
) {
    val waiting: Int get() = queue.waiting
    val rejected: Int get() = queue.rejected
}

@HiltViewModel
class RentalOpsHomeViewModel @Inject constructor(private val repo: RentalOpsRepository) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repo.access, repo.observeCounts(), repo.observeQueue()) { a, c, q -> HomeState(a, c, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun sync() = repo.requestSync()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalOpsHomeScreen(onNavigate: (String) -> Unit, onBack: (() -> Unit)?, vm: RentalOpsHomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(1_200); refreshing = false } }
    Scaffold(
        topBar = {
            OpsTopBar(stringResource(R.string.rentalops_title), onBack) {
                IconButton(onClick = vm::sync) { Icon(Icons.Filled.Sync, contentDescription = stringResource(R.string.rentalops_sync_now)) }
            }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; vm.sync() },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = AvagoSpacing.readableMaxWidth),
                contentPadding = PaddingValues(vertical = AvagoSpacing.sm),
            ) {
                if (s.access.statements || s.access.tasks) {
                    item(key = "kpis", contentType = "kpis") { CountsRow(s.counts, onOpenTasks = if (s.access.tasks) { { onNavigate(RentalOpsRoutes.TASKS) } } else null) }
                }
                if (s.waiting > 0 || s.rejected > 0) {
                    item(key = "queue", contentType = "banner") {
                        ErrorBanner(
                            stringResource(R.string.rentalops_queue_summary, s.waiting, s.rejected),
                            tone = if (s.rejected > 0) StatusTone.Danger else StatusTone.Info,
                            icon = if (s.rejected > 0) Icons.Filled.ErrorOutline else Icons.Filled.CloudUpload,
                            onRetry = vm::sync,
                            retryLabel = stringResource(R.string.rentalops_sync_now),
                        )
                    }
                }
                if (!s.access.any) {
                    item(key = "none", contentType = "empty") {
                        EmptyState(stringResource(R.string.rentalops_no_access), icon = Icons.Filled.Lock)
                    }
                }
                val field = buildList {
                    if (s.access.tasks) add(Entry(Icons.Filled.LocalShipping, StatusTone.Info, R.string.rentalops_tasks_title, R.string.rentalops_tasks_sub, RentalOpsRoutes.TASKS))
                    if (s.access.assign) add(Entry(Icons.Filled.Inventory2, StatusTone.Accent, R.string.rentalops_assign_title, R.string.rentalops_assign_sub, RentalOpsRoutes.ASSIGN))
                    if (s.access.transfers) add(Entry(Icons.AutoMirrored.Filled.CompareArrows, StatusTone.Accent, R.string.rentalops_transfers_title, R.string.rentalops_transfers_sub, RentalOpsRoutes.TRANSFERS))
                }
                val finance = buildList {
                    if (s.access.statements) add(Entry(Icons.AutoMirrored.Filled.ReceiptLong, StatusTone.Success, R.string.rentalops_statement_title, R.string.rentalops_statement_sub, RentalOpsRoutes.STATEMENT))
                    if (s.access.aging) add(Entry(Icons.Filled.BarChart, StatusTone.Warning, R.string.rentalops_aging_title, R.string.rentalops_aging_sub, RentalOpsRoutes.AGING))
                }
                if (field.isNotEmpty()) {
                    item(key = "field", contentType = "card") { EntryCard(stringResource(R.string.rentalops_section_field), field, onNavigate) }
                }
                if (finance.isNotEmpty()) {
                    item(key = "finance", contentType = "card") { EntryCard(stringResource(R.string.rentalops_section_finance), finance, onNavigate) }
                }
                if (s.access.any) {
                    item(key = "settings", contentType = "card") {
                        EntryCard(
                            null,
                            listOf(Entry(Icons.Filled.NotificationsActive, StatusTone.Neutral, R.string.rentalops_settings_title, R.string.rentalops_settings_sub, RentalOpsRoutes.SETTINGS)),
                            onNavigate,
                        )
                    }
                }
            }
        }
    }
}

private data class Entry(val icon: ImageVector, val tone: StatusTone, @StringRes val title: Int, @StringRes val sub: Int, val route: String)

@Composable
private fun CountsRow(c: OpsCounts, onOpenTasks: (() -> Unit)?) {
    KpiGrid(
        listOf(
            KpiItem(stringResource(R.string.rentalops_counts_out), c.out.toString(), Icons.Filled.Inventory2, StatusTone.Info, onClick = onOpenTasks),
            KpiItem(
                stringResource(R.string.rentalops_counts_due_today), c.dueToday.toString(), Icons.Filled.Today,
                if (c.dueToday > 0) StatusTone.Warning else StatusTone.Neutral, onClick = onOpenTasks,
            ),
            KpiItem(
                stringResource(R.string.rentalops_counts_overdue), c.overdue.toString(), Icons.Filled.Warning,
                if (c.overdue > 0) StatusTone.Danger else StatusTone.Neutral, onClick = onOpenTasks,
            ),
        ),
        Modifier.padding(vertical = AvagoSpacing.sm),
    )
}

@Composable
private fun EntryCard(title: String?, entries: List<Entry>, onNavigate: (String) -> Unit) {
    SectionCard(title = title) {
        entries.forEachIndexed { i, e ->
            if (i > 0) CardDivider(Modifier.padding(start = AvagoSpacing.listInset))
            ListItem(
                headlineContent = { Text(stringResource(e.title)) },
                supportingContent = { Text(stringResource(e.sub)) },
                leadingContent = { LeadingBadge(e.icon, e.tone) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = cardListItemColors(),
                modifier = Modifier
                    .heightIn(min = MinTouch)
                    .clickable(onClickLabel = stringResource(R.string.rentalops_open), role = Role.Button) { onNavigate(e.route) },
            )
        }
    }
}
