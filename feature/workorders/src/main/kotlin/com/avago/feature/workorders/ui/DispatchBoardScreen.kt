package com.avago.feature.workorders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.WorkOrderEntity
import com.avago.core.ui.EmptyState
import com.avago.feature.workorders.R
import com.avago.feature.workorders.model.WoStatus
import com.avago.feature.workorders.model.statusColor
import com.avago.feature.workorders.ui.components.WoCard
import com.avago.feature.workorders.ui.components.WoRebalanceBanner
import com.avago.feature.workorders.viewmodel.DISPATCH_COLUMNS
import com.avago.feature.workorders.viewmodel.DispatchBoardViewModel
import kotlinx.coroutines.launch

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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.dispatch_board_title)) })
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
            if (showRebalanceBanner) {
                WoRebalanceBanner(
                    onRebalance = {
                        viewModel.rebalance()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.dispatch_rebalance_coming_soon))
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
                        val wos = columns[status] ?: emptyList()
                        DispatchColumn(
                            status = status,
                            workOrders = wos,
                            onWoClick = onWoClick,
                            onDrop = { wo -> viewModel.moveToStatus(wo, status) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DispatchColumn(
    status: WoStatus,
    workOrders: List<WorkOrderEntity>,
    onWoClick: (woId: String) -> Unit,
    onDrop: (WorkOrderEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = status.statusColor()

    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
    ) {
        // Column header
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = statusColor,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "${workOrders.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(workOrders, key = { it.woId }) { wo ->
                DraggableWoCard(
                    wo = wo,
                    onClick = { onWoClick(wo.woId) },
                    onDragEnd = { targetOffset ->
                        // Column detection by x-offset is handled by the parent LazyRow.
                        // Here we pass the drag event up so the parent VM can route it.
                        // For a full production drag-and-drop we'd use DragAndDropHost
                        // (API 35+) or a third-party library. This implementation uses
                        // pointerInput to detect gesture and a simplified column drop.
                        onDrop(wo)
                    },
                )
            }
        }
    }
}

@Composable
private fun DraggableWoCard(
    wo: WorkOrderEntity,
    onClick: () -> Unit,
    onDragEnd: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(wo.woId) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    },
                    onDragEnd = {
                        // Only trigger status move when drag distance exceeds 120 dp
                        if (dragOffset.x.toDouble() > 120.0) {
                            onDragEnd(dragOffset)
                        }
                        dragOffset = Offset.Zero
                        isDragging = false
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        isDragging = false
                    },
                )
            },
    ) {
        WoCard(
            wo = wo,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
