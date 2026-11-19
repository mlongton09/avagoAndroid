package com.avago.feature.inventory.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.formatQty
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal

data class StockingLevelItem(
    val stockingLevel: StockingPolicy,
    val partName: String?,
    val partSku: String?,
    val locationName: String?,
)

data class StockingLevelsUiState(
    val items: List<StockingLevelItem> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

@HiltViewModel
class StockingLevelsViewModel @Inject constructor(
    inventory: InventoryRepository,
    locations: LocationRepository,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<StockingLevelsUiState> = combine(
        combine(
            inventory.observePolicies(),
            inventory.observeProductStock(includeArchived = true),
            locations.observeLocations(includeArchived = true),
        ) { policies, stock, locs ->
            val products = stock.associateBy { it.product.id }
            val locationNames = locs.associate { it.id to it.name }
            policies
                .map { policy ->
                    val product = products[policy.productId]?.product
                    StockingLevelItem(
                        stockingLevel = policy,
                        partName = product?.name,
                        partSku = product?.sku,
                        locationName = locationNames[policy.locationId],
                    )
                }
                .sortedWith(compareBy({ it.partName ?: "" }, { it.locationName ?: "" }, { it.stockingLevel.id }))
        },
        searchQuery,
    ) { items, query ->
        val filteredItems = if (query.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.partName?.contains(query, ignoreCase = true) == true ||
                    item.partSku?.contains(query, ignoreCase = true) == true
            }
        }
        StockingLevelsUiState(
            items = filteredItems,
            isLoading = false,
            searchQuery = query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockingLevelsUiState(),
    )

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockingLevelsScreen(
    onBack: () -> Unit,
    viewModel: StockingLevelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.stocking_levels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AvagoSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                placeholder = stringResource(R.string.stocking_levels_search_hint),
            )

            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState(
                    message = stringResource(R.string.stocking_levels_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.stockingLevel.id }) { item ->
                        StockingLevelCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockingLevelCard(
    item: StockingLevelItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.partName ?: item.stockingLevel.productId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(item.partSku, item.locationName).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StockValue(
                    label = stringResource(R.string.stocking_level_min_qty),
                    value = item.stockingLevel.minQty,
                )
                StockValue(
                    label = stringResource(R.string.stocking_level_max_qty),
                    value = item.stockingLevel.maxQty,
                )
                StockValue(
                    label = stringResource(R.string.stocking_level_reorder_qty),
                    value = item.stockingLevel.reorderQty,
                )
                StockValue(
                    label = stringResource(R.string.stocking_level_safety_stock),
                    value = item.stockingLevel.safetyQty,
                )
            }
        }
    }
}

@Composable
private fun RowScope.StockValue(
    label: String,
    value: BigDecimal?,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.formatQty() ?: stringResource(R.string.stocking_level_not_set),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
