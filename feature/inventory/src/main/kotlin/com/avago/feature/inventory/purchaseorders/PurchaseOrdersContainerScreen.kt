package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import com.avago.feature.inventory.cyclecounts.CYCLE_COUNT_STATUSES
import com.avago.feature.inventory.cyclecounts.CycleCountCreateSheet
import com.avago.feature.inventory.cyclecounts.CycleCountListViewModel
import com.avago.feature.inventory.cyclecounts.cycleCountStatusLabel
import com.avago.feature.inventory.warehouse.ReorderItemCard
import com.avago.feature.inventory.warehouse.WarehouseReorderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrdersContainerScreen(
    onPoClick: (String) -> Unit,
    onCreatePo: () -> Unit,
    onCountClick: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val poViewModel: PurchaseOrderListViewModel = hiltViewModel()
    val cycleCountViewModel: CycleCountListViewModel = hiltViewModel()
    val reorderViewModel: WarehouseReorderViewModel = hiltViewModel()

    val cycleState by cycleCountViewModel.uiState.collectAsStateWithLifecycle()

    if (cycleState.showCreateSheet) {
        CycleCountCreateSheet(onDismiss = cycleCountViewModel::dismissCreate)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.po_list_title)) })
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Orders") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Cycle Counts") },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Reorder") },
                    )
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = onCreatePo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.po_create))
                }
                1 -> FloatingActionButton(
                    onClick = cycleCountViewModel::openCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cycle_count_create))
                }
                2 -> FloatingActionButton(
                    onClick = onCreatePo,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Create Purchase Order")
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> PoTabContent(
                viewModel = poViewModel,
                onPoClick = onPoClick,
                modifier = Modifier.padding(padding),
            )
            1 -> CycleCountTabContent(
                viewModel = cycleCountViewModel,
                onCountClick = onCountClick,
                modifier = Modifier.padding(padding),
            )
            2 -> ReorderTabContent(
                viewModel = reorderViewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PoTabContent(
    viewModel: PurchaseOrderListViewModel,
    onPoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedStatus == null,
                    onClick = { viewModel.setStatus(null) },
                    label = { Text(stringResource(R.string.po_status_all)) },
                )
            }
            items(PO_STATUSES) { status ->
                FilterChip(
                    selected = state.selectedStatus == status,
                    onClick = { viewModel.setStatus(status) },
                    label = { Text(poStatusLabel(status)) },
                )
            }
        }
        if (state.filtered.isEmpty() && !state.isLoading) {
            EmptyState(message = stringResource(R.string.po_empty))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.filtered, key = { it.po.poId }) { item ->
                    PoCard(item = item, onClick = { onPoClick(item.po.poId) })
                }
            }
        }
    }
}

@Composable
private fun CycleCountTabContent(
    viewModel: CycleCountListViewModel,
    onCountClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedStatus == null,
                    onClick = { viewModel.setStatus(null) },
                    label = { Text(stringResource(R.string.po_status_all)) },
                )
            }
            items(CYCLE_COUNT_STATUSES) { status ->
                FilterChip(
                    selected = state.selectedStatus == status,
                    onClick = { viewModel.setStatus(status) },
                    label = { Text(cycleCountStatusLabel(status)) },
                )
            }
        }
        if (state.filtered.isEmpty() && !state.isLoading) {
            EmptyState(message = stringResource(R.string.cycle_count_empty))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.filtered, key = { it.cycleCountId }) { count ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCountClick(count.cycleCountId) },
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = count.cycleCountId.take(8),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = count.locationId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(cycleCountStatusLabel(count.status)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderTabContent(
    viewModel: WarehouseReorderViewModel,
    modifier: Modifier = Modifier,
) {
    val reorderItems by viewModel.reorderItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (reorderItems.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "All parts are sufficiently stocked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(reorderItems, key = { it.part.partId }) { item ->
                ReorderItemCard(item = item)
            }
        }
    }
}
