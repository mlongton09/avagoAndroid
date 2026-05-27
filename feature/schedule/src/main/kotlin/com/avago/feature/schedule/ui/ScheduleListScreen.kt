package com.avago.feature.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.schedule.R
import com.avago.feature.schedule.ui.components.ScheduleCard
import com.avago.feature.schedule.viewmodel.ScheduleListViewModel
import com.avago.feature.schedule.viewmodel.ScheduleStatusFilter
import com.avago.feature.schedule.viewmodel.ScheduleTypeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    onScheduleClick: (scheduleId: String) -> Unit,
    onAddSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    assetId: String? = null,
    viewModel: ScheduleListViewModel = hiltViewModel(),
) {
    // Propagate optional asset filter
    LaunchedEffect(assetId) {
        viewModel.assetId.value = assetId
    }

    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (assetId != null)
                            stringResource(R.string.schedule_list_title)
                        else
                            stringResource(R.string.schedule_list_title)
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSchedule,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.schedule_add_fab_description),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val statusChips = listOf(
                ScheduleStatusFilter.ALL to stringResource(R.string.schedule_filter_all),
                ScheduleStatusFilter.DUE_SOON to stringResource(R.string.schedule_filter_due_soon),
                ScheduleStatusFilter.UPCOMING to stringResource(R.string.schedule_filter_upcoming),
                ScheduleStatusFilter.OVERDUE to stringResource(R.string.schedule_filter_overdue),
            )
            val typeChips = listOf(
                ScheduleTypeFilter.ALL to stringResource(R.string.schedule_type_all),
                ScheduleTypeFilter.BY_DATE to stringResource(R.string.schedule_type_by_date),
                ScheduleTypeFilter.BY_METER to stringResource(R.string.schedule_type_by_meter),
            )

            // ── Status filter chips ───────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(statusChips) { (filter, label) ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { viewModel.onStatusFilterChanged(filter) },
                        label = { Text(label) },
                    )
                }
            }

            // ── Type filter chips ─────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(typeChips) { (filter, label) ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick = { viewModel.onTypeFilterChanged(filter) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── List with pull-to-refresh ──────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (schedules.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.schedule_list_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(schedules, key = { it.scheduleId }) { schedule ->
                            ScheduleCard(
                                schedule = schedule,
                                onClick = { onScheduleClick(schedule.scheduleId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
