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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.StockingLevelEntity
import com.avago.core.ui.AvagoSearchBar
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class StockingLevelItem(
    val stockingLevel: StockingLevelEntity,
    val partName: String?,
    val partSku: String?,
)

data class StockingLevelsUiState(
    val items: List<StockingLevelItem> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

@HiltViewModel
class StockingLevelsViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StockingLevelsUiState> = combine(
        identityManager.activeAccountId.flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(emptyList<StockingLevelItem>())
            } else {
                val db = dbFactory.get(accountId)
                combine(
                    db.stockingLevelDao().observeAll(accountId),
                    db.partDao().observeAll(accountId),
                ) { stockingLevels, parts ->
                    val partsById = parts.associateBy { it.partId }
                    stockingLevels
                        .map { level ->
                            val part = partsById[level.partId]
                            StockingLevelItem(
                                stockingLevel = level,
                                partName = part?.name,
                                partSku = part?.sku,
                            )
                        }
                        .sortedWith(
                            compareBy(
                                { it.partName ?: "" },
                                { it.partSku ?: "" },
                                { it.stockingLevel.stockingLevelId },
                            ),
                        )
                }
            }
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
                    items(state.items, key = { it.stockingLevel.stockingLevelId }) { item ->
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
                text = item.partName ?: item.stockingLevel.partId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.partSku?.let { sku ->
                Text(
                    text = sku,
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
                    value = item.stockingLevel.safetyStock,
                )
            }
        }
    }
}

@Composable
private fun RowScope.StockValue(
    label: String,
    value: Double?,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.let { "%.2f".format(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
