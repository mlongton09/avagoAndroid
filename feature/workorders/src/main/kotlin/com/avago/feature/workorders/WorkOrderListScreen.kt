package com.avago.feature.workorders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.remember
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.core.ui.rememberScrollAwareHeaderState
import com.avago.feature.workorders.ui.components.WoCard
import com.avago.feature.workorders.viewmodel.WoHorizon
import com.avago.feature.workorders.viewmodel.WoListFilter
import com.avago.feature.workorders.viewmodel.WorkOrderListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderListScreen(
    onWoClick: (woId: String) -> Unit,
    onCreateWo: () -> Unit,
    onOpenCalendar: () -> Unit = {},
    onOpenDispatchBoard: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkOrderListViewModel = hiltViewModel(),
) {
    val buckets by viewModel.buckets.collectAsStateWithLifecycle()
    val assetLabels by viewModel.assetLabels.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val horizon by viewModel.horizon.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val canCreateWo by viewModel.canCreateWo.collectAsStateWithLifecycle()
    val canOpenDispatch by viewModel.canOpenDispatch.collectAsStateWithLifecycle()

    val horizonOptions = listOf(
        WoHorizon.NOW  to stringResource(R.string.wo_horizon_now),
        WoHorizon.NEXT to stringResource(R.string.wo_horizon_next),
        WoHorizon.LATER to stringResource(R.string.wo_horizon_later),
    )

    val scopeOptions = listOf(
        WoListFilter.MINE to stringResource(R.string.wo_scope_mine),
        WoListFilter.ALL  to stringResource(R.string.wo_scope_all),
    )

    val scrollAwareState = rememberScrollAwareHeaderState()
    val headerVisible by scrollAwareState.headerVisible
    val headerProgress by animateFloatAsState(
        targetValue = if (headerVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "wo_header_progress",
    )
    // Filter row (~56dp) + search bar (~52dp) + templates action (~40dp).
    val headerHeightDp = 148.dp
    val density = LocalDensity.current
    val headerHeightPx = with(density) { headerHeightDp.toPx() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (canCreateWo) {
                FloatingActionButton(
                    onClick = onCreateWo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.wo_create_fab_description),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .nestedScroll(scrollAwareState),
        ) {
            // ── Scrollable content ────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (buckets.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(with(density) { (headerHeightPx * headerProgress).toDp() }))
                        EmptyState(
                            message = if (filter == WoListFilter.MINE) {
                                stringResource(R.string.wo_all_caught_up)
                            } else {
                                stringResource(R.string.wo_no_open)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = with(density) { (headerHeightPx * headerProgress).toDp() },
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 8.dp,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        syncError?.let { msg ->
                            item(key = "sync_error") {
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
                                            text = stringResource(R.string.wo_list_retry),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }

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
                                    assetLabel = assetLabels[wo.assetId],
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // ── Floating filter + search header — slides up on scroll down ────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -headerHeightPx * (1f - headerProgress)
                        alpha = headerProgress
                    },
            ) {
                Column {
                    // Now / Next / Later + Mine / All + Calendar + Dispatch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                            horizonOptions.forEachIndexed { index, (h, label) ->
                                SegmentedButton(
                                    selected = horizon == h,
                                    onClick = { viewModel.onHorizonChanged(h) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = horizonOptions.size,
                                    ),
                                    label = { Text(label) },
                                )
                            }
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(110.dp)) {
                            scopeOptions.forEachIndexed { index, (s, label) ->
                                SegmentedButton(
                                    selected = filter == s,
                                    onClick = { viewModel.onScopeChanged(s) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = scopeOptions.size,
                                    ),
                                    label = { Text(label) },
                                )
                            }
                        }
                        IconButton(onClick = onOpenCalendar) {
                            Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.wo_calendar_title))
                        }
                        if (canOpenDispatch) {
                            IconButton(onClick = onOpenDispatchBoard) {
                                Icon(Icons.Default.Dashboard, contentDescription = stringResource(R.string.dispatch_board_title))
                            }
                        }
                    }
                    HorizontalDivider()
                    AvagoSearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        placeholder = stringResource(R.string.wo_search_placeholder),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onOpenTemplates) {
                            Text(stringResource(R.string.wo_templates_title))
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
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
