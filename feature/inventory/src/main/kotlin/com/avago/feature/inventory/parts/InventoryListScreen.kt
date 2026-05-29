package com.avago.feature.inventory.parts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

private val ColorInStock = Color(0xFF4CAF50)
private val ColorLowStock = Color(0xFFFF9800)
private val ColorOutOfStock = Color(0xFFF44336)
private val ColorTotal = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen(
    onPartClick: (String) -> Unit,
    onAddPart: () -> Unit,
    viewModel: InventoryListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val canCreatePart by viewModel.canCreatePart.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.inventory_title)) })
        },
        floatingActionButton = {
            if (canCreatePart) {
                FloatingActionButton(
                    onClick = onAddPart,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.inventory_add_part))
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AvagoSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    placeholder = stringResource(R.string.inventory_search_hint),
                )

                // Summary statistics bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile(
                        label = "Total",
                        count = state.totalCount,
                        color = ColorTotal,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "In Stock",
                        count = state.inStockCount,
                        color = ColorInStock,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Low",
                        count = state.lowStockCount,
                        color = ColorLowStock,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Out",
                        count = state.outOfStockCount,
                        color = ColorOutOfStock,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Category filter chips
                if (state.categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedCategory == null,
                                onClick = { viewModel.setCategory(null) },
                                label = { Text(stringResource(R.string.inventory_all_categories)) },
                            )
                        }
                        items(state.categories) { cat ->
                            FilterChip(
                                selected = state.selectedCategory == cat,
                                onClick = { viewModel.setCategory(cat) },
                                label = { Text(cat) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Stock status filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.stockFilter == "all",
                            onClick = { viewModel.setStockFilter("all") },
                            label = { Text("All") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.stockFilter == "in_stock",
                            onClick = { viewModel.setStockFilter("in_stock") },
                            label = { Text("In Stock") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.stockFilter == "low_stock",
                            onClick = { viewModel.setStockFilter("low_stock") },
                            label = { Text("Low Stock") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.stockFilter == "out_of_stock",
                            onClick = { viewModel.setStockFilter("out_of_stock") },
                            label = { Text("Out of Stock") },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                if (state.displayList.isEmpty() && !state.isLoading) {
                    EmptyState(message = stringResource(R.string.inventory_empty))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.displayList) { entry ->
                            when (entry) {
                                is InventoryListEntry.Header -> {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                }
                                is InventoryListEntry.PartRow -> {
                                    PartCard(
                                        item = entry.item,
                                        onClick = { onPartClick(entry.item.part.partId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PartCard(
    item: PartListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.part.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                item.part.sku?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.part.category?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val onHand = item.inventory?.quantityOnHand ?: 0.0
                Text(
                    text = stringResource(R.string.inventory_on_hand),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.2f".format(onHand),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (item.needsReorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.inventory_reorder_needed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
