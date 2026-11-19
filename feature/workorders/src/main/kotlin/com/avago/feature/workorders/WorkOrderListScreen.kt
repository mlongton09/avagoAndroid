package com.avago.feature.workorders

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.core.ui.rememberScrollAwareHeaderState
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.ui.components.WoCard
import com.avago.feature.workorders.ui.sheets.RescheduleSheet
import com.avago.feature.workorders.ui.components.woStatusLabel
import com.avago.feature.workorders.viewmodel.WoBucketKey
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
    val isFilteredEmpty by viewModel.isFilteredEmpty.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val horizonOptions = listOf(
        WoHorizon.NOW  to stringResource(R.string.wo_horizon_now),
        WoHorizon.NEXT to stringResource(R.string.wo_horizon_next),
        WoHorizon.LATER to stringResource(R.string.wo_horizon_later),
    )

    val scopeOptions = listOf(
        WoListFilter.MINE to stringResource(R.string.wo_scope_mine),
        WoListFilter.ALL  to stringResource(R.string.wo_scope_all),
    )

    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val scrollAwareState = rememberScrollAwareHeaderState()
    val headerVisible by scrollAwareState.headerVisible
    val headerProgress by animateFloatAsState(
        targetValue = if (headerVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "wo_header_progress",
    )
    // Title row (~52dp) + filter row (~56dp) + optional search bar (~52dp).
    val headerHeightDp by animateDpAsState(
        targetValue = if (showSearchBar) 160.dp else 108.dp,
        label = "wo_header_height",
    )
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
                        if (isFilteredEmpty) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.wo_list_no_matches_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.wo_list_no_matches_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = viewModel::clearSearchAndFilters,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.wo_list_clear_filters))
                                }
                            }
                        } else {
                            EmptyState(
                                message = if (filter == WoListFilter.MINE) {
                                    stringResource(R.string.wo_all_caught_up)
                                } else {
                                    stringResource(R.string.wo_no_open)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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
                            stickyHeader(key = "header_${bucket.key.name}") {
                                BucketHeader(
                                    label = bucketLabel(bucket.key),
                                    count = bucket.items.size,
                                )
                            }
                            items(bucket.items, key = { it.id }) { wo ->
                                var rescheduleTarget by remember { mutableStateOf<String?>(null) }
                                SwipeToDismissBox(
                                    state = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                rescheduleTarget = wo.id
                                            }
                                            false // don't actually dismiss — just reveal the action
                                        },
                                        positionalThreshold = { it * 0.35f },
                                    ),
                                    enableDismissFromEndToStart = true,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(bottom = 8.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.secondary),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(end = 20.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Text(
                                                    text = stringResource(R.string.reschedule_title),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    },
                                ) {
                                    val rescheduleLabel = stringResource(R.string.reschedule_title)
                                    WoCard(
                                        wo = wo,
                                        onClick = { onWoClick(wo.id) },
                                        assetLabel = wo.primaryAssetId?.let { assetLabels[it] },
                                        // TalkBack can't swipe ? expose reschedule as a custom action.
                                        modifier = Modifier.semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction(rescheduleLabel) {
                                                    rescheduleTarget = wo.id
                                                    true
                                                },
                                            )
                                        },
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (rescheduleTarget == wo.id) {
                                    val currentDue = wo.dueAt?.let {
                                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                    }
                                    RescheduleSheet(
                                        currentDueDate = currentDue,
                                        onDismiss = { rescheduleTarget = null },
                                        onConfirm = { newDate ->
                                            val ms = newDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            viewModel.rescheduleWo(wo.id, ms)
                                            rescheduleTarget = null
                                        },
                                    )
                                }
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
                    // ── Title row: icon + "Work Orders" (left) + Filter/Search/Calendar (right) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Avago branded icon — mirrors iOS AppTheme.makeBrandedTitleView():
                        // UIImageView(named:"AppIconImage") 22pt, cornerRadius 5
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        // iOS uses 7pt spacing between icon and text
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = stringResource(R.string.wo_list_title),
                            // largeTitleFont() = systemFont(22, .bold) → headlineMedium in app theme
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // Filter icon with active-filter badge
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                BadgedBox(
                                    badge = {
                                        if (statusFilter.isNotEmpty()) {
                                            Badge { Text("${statusFilter.size}") }
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.wo_filter_fab_description),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                            ) {
                                if (statusFilter.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.wo_filter_clear)) },
                                        onClick = {
                                            viewModel.clearStatusFilter()
                                            showFilterMenu = false
                                        },
                                    )
                                    HorizontalDivider()
                                }
                                WoStatus.entries.forEach { status ->
                                    val isSelected = status.key in statusFilter
                                    DropdownMenuItem(
                                        text = { Text(woStatusLabel(status.key)) },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null,
                                            )
                                        },
                                        onClick = { viewModel.toggleStatusFilter(status.key) },
                                    )
                                }
                            }
                        }
                        // Search icon — toggles search bar
                        IconButton(
                            onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) viewModel.onSearchQueryChanged("")
                            },
                        ) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(R.string.wo_search_placeholder),
                            )
                        }
                        // Calendar icon
                        IconButton(onClick = onOpenCalendar) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = stringResource(R.string.wo_calendar_title),
                            )
                        }
                    }

                    // ── Filter row: Now/Next/Later + Mine/All segmented controls only ──
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
                    }
                    HorizontalDivider()
                    if (showSearchBar) {
                        AvagoSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            placeholder = stringResource(R.string.wo_search_placeholder),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun bucketLabel(key: WoBucketKey): String = stringResource(
    when (key) {
        WoBucketKey.OVERDUE -> R.string.wo_bucket_overdue
        WoBucketKey.DUE_TODAY -> R.string.wo_bucket_due_today
        WoBucketKey.THIS_WEEK -> R.string.wo_bucket_this_week
        WoBucketKey.THIS_MONTH -> R.string.wo_bucket_this_month
        WoBucketKey.LATER -> R.string.wo_bucket_later
        WoBucketKey.COMPLETED -> R.string.wo_bucket_completed
    },
)

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
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .semantics(mergeDescendants = true) { heading() },
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
