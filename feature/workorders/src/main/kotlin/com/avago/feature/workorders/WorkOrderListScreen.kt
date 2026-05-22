package com.avago.feature.workorders

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.workorders.ui.components.WoCard
import com.avago.feature.workorders.viewmodel.WoHorizon
import com.avago.feature.workorders.viewmodel.WoListFilter
import com.avago.feature.workorders.viewmodel.WorkOrderListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderListScreen(
    onWoClick: (woId: String) -> Unit,
    onCreateWo: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderListViewModel = hiltViewModel(),
) {
    val buckets by viewModel.buckets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val horizon by viewModel.horizon.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()

    val scopeTabs = listOf(
        WoListFilter.ALL to stringResource(R.string.wo_tab_all),
        WoListFilter.OPEN to stringResource(R.string.wo_tab_open),
        WoListFilter.MINE to stringResource(R.string.wo_tab_mine),
        WoListFilter.OVERDUE to stringResource(R.string.wo_tab_overdue),
    )

    val horizonChips = listOf(
        WoHorizon.ALL_TIME to stringResource(R.string.wo_horizon_all_time),
        WoHorizon.THIS_WEEK to stringResource(R.string.wo_horizon_this_week),
        WoHorizon.THIS_MONTH to stringResource(R.string.wo_horizon_this_month),
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wo_list_title)) },
                actions = {
                    IconButton(onClick = { /* filter sheet — future */ }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.wo_filter_fab_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateWo) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.wo_create_fab_description),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // Horizon filter chips (horizontal scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                horizonChips.forEach { (h, label) ->
                    FilterChip(
                        selected = horizon == h,
                        onClick = { viewModel.onHorizonChanged(h) },
                        label = { Text(label) },
                    )
                }
            }

            // Scope tab row
            PrimaryTabRow(selectedTabIndex = scopeTabs.indexOfFirst { it.first == filter }) {
                scopeTabs.forEachIndexed { _, (tabFilter, label) ->
                    Tab(
                        selected = filter == tabFilter,
                        onClick = { viewModel.onFilterChanged(tabFilter) },
                        text = { Text(label) },
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.wo_search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
            )

            syncError?.let { msg ->
                Surface(
                    onClick = viewModel::refresh,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Bucketed list with pull-to-refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (buckets.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.wo_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        buckets.forEach { bucket ->
                            stickyHeader(key = "header_${bucket.label}") {
                                BucketHeader(
                                    label = bucket.label,
                                    count = bucket.items.size,
                                )
                            }
                            items(bucket.items, key = { it.woId }) { wo ->
                                WoCard(
                                    wo = wo,
                                    onClick = { onWoClick(wo.woId) },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
