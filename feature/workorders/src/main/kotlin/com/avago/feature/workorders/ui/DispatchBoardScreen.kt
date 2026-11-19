package com.avago.feature.workorders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.WorkOrder
import com.avago.core.ui.EmptyState
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.statusColor
import com.avago.feature.workorders.ui.components.WoCard
import com.avago.feature.workorders.ui.components.WoRebalanceBanner
import com.avago.feature.workorders.ui.components.woStatusLabel
import com.avago.feature.workorders.viewmodel.DISPATCH_COLUMNS
import com.avago.feature.workorders.viewmodel.DispatchBoardViewModel
import com.avago.feature.workorders.viewmodel.boardMoveTargets
import com.avago.feature.workorders.viewmodel.canUndoMove
import kotlinx.coroutines.launch

/**
 * Kanban dispatch board. Mobile adaptation of the web drag-and-drop board:
 * long-press a card (or use the TalkBack "Move to…" action) to open a bottom sheet
 * listing the target columns. Moving to Complete opens the WO detail so the
 * close-out (codes, notes, meter) is captured instead of a bare status flip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchBoardScreen(
    onWoClick: (woId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DispatchBoardViewModel = hiltViewModel(),
) {
    val dispatchEnabled by viewModel.dispatchEnabled.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val showRebalanceBanner by viewModel.showRebalanceBanner.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    var showSearch by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<WorkOrder?>(null) }
    // Resolved in composition so they follow configuration (locale) changes.
    val movedMessages = DISPATCH_COLUMNS.associateWith {
        stringResource(R.string.dispatch_moved_to, woStatusLabel(it.key))
    }
    val undoLabel = stringResource(R.string.dispatch_undo)
    val rebalanceSoon = stringResource(R.string.dispatch_rebalance_coming_soon)

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.dispatch_board_title)) },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.searchQuery.value = ""
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.dispatch_board_search_hint),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (!dispatchEnabled) {
            EmptyState(
                message = stringResource(R.string.dispatch_board_disabled),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text(stringResource(R.string.dispatch_board_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.wo_search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.dispatch_board_move_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (showRebalanceBanner) {
                WoRebalanceBanner(
                    onRebalance = {
                        viewModel.rebalance()
                        scope.launch {
                            snackbarHostState.showSnackbar(rebalanceSoon)
                        }
                    },
                    onDismiss = viewModel::dismissBanner,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(DISPATCH_COLUMNS) { status ->
                        DispatchColumn(
                            status = status,
                            workOrders = columns[status] ?: emptyList(),
                            onWoClick = onWoClick,
                            onRequestMove = { wo ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                moveTarget = wo
                            },
                        )
                    }
                }
            }
        }
    }

    moveTarget?.let { wo ->
        MoveToSheet(
            wo = wo,
            onDismiss = { moveTarget = null },
            onMove = { target ->
                moveTarget = null
                if (target == WoStatus.COMPLETE) {
                    // Completion needs close-out data — hand off to the detail screen.
                    onWoClick(wo.id)
                } else {
                    viewModel.moveToStatus(wo, target)
                    val message = movedMessages.getValue(target)
                    val undoable = canUndoMove(WoStatus.fromDomain(wo.status), target)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = if (undoable) undoLabel else null,
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(wo)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToSheet(
    wo: WorkOrder,
    onDismiss: () -> Unit,
    onMove: (WoStatus) -> Unit,
) {
    val current = WoStatus.fromDomain(wo.status)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.dispatch_move_title, wo.title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            (boardMoveTargets(current) + listOfNotNull(WoStatus.COMPLETE.takeIf { current != it && !current.domain.isTerminal })).distinct().forEach { status ->
                val color = status.statusColor()
                ListItem(
                    headlineContent = { Text(woStatusLabel(status.key)) },
                    supportingContent = if (status == WoStatus.COMPLETE) {
                        { Text(stringResource(R.string.dispatch_complete_via_detail)) }
                    } else null,
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable { onMove(status) },
                )
            }
        }
    }
}

@Composable
private fun DispatchColumn(
    status: WoStatus,
    workOrders: List<WorkOrder>,
    onWoClick: (woId: String) -> Unit,
    onRequestMove: (WorkOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = status.statusColor()
    val label = woStatusLabel(status.key)
    val countLabel = pluralStringResource(R.plurals.dispatch_column_count, workOrders.size, workOrders.size)

    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = statusColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (workOrders.isEmpty()) {
            Text(
                text = stringResource(R.string.dispatch_board_empty_column),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(workOrders, key = { it.id }) { wo ->
                val moveLabel = stringResource(R.string.dispatch_move_action)
                WoCard(
                    wo = wo,
                    onClick = { onWoClick(wo.id) },
                    onLongClick = { onRequestMove(wo) },
                    onLongClickLabel = moveLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveLabel) { onRequestMove(wo); true },
                            )
                        },
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
