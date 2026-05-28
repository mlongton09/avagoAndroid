package com.avago.feature.inventory.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.db.entity.PartEntity
import com.avago.core.data.db.entity.StockingLevelEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

data class ReorderItem(
    val part: PartEntity,
    val stockingLevel: StockingLevelEntity,
    val totalOnHand: Double,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class WarehouseReorderViewModel @Inject constructor(
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val reorderItems: StateFlow<List<ReorderItem>> = identityManager.activeAccountId
        .flatMapLatest { accountId ->
            if (accountId == null) return@flatMapLatest flowOf(emptyList())
            val db = dbFactory.get(accountId)
            combine(
                db.partDao().observeAll(accountId),
                db.inventoryDao().observeAll(accountId),
                db.stockingLevelDao().observeAll(accountId),
            ) { parts, inventoryRows, stockingLevels ->
                _isLoading.value = false

                // Sum on-hand qty per part across all locations
                val onHandByPartId: Map<String, Double> = inventoryRows
                    .groupBy { it.partId }
                    .mapValues { (_, rows) -> rows.sumOf { it.quantityOnHand } }

                // Index stocking levels by partId (use the first level found per part)
                val levelByPartId: Map<String, StockingLevelEntity> = stockingLevels
                    .groupBy { it.partId }
                    .mapValues { (_, levels) -> levels.first() }

                parts.mapNotNull { part ->
                    val level = levelByPartId[part.partId] ?: return@mapNotNull null
                    val reorderQty = level.reorderQty ?: return@mapNotNull null
                    val onHand = onHandByPartId[part.partId] ?: 0.0
                    if (onHand <= reorderQty) {
                        ReorderItem(part = part, stockingLevel = level, totalOnHand = onHand)
                    } else {
                        null
                    }
                }.sortedBy { it.part.name }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseReorderScreen(
    onBack: () -> Unit,
    onCreatePo: () -> Unit,
    viewModel: WarehouseReorderViewModel = hiltViewModel(),
) {
    val reorderItems by viewModel.reorderItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reorder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = "Create Purchase Order",
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { /* data refreshes automatically via Flow */ },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (reorderItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "All parts are sufficiently stocked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(reorderItems, key = { it.part.partId }) { item ->
                        ReorderItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReorderItemCard(item: ReorderItem) {
    val onHand = item.totalOnHand
    val reorderQty = item.stockingLevel.reorderQty ?: 0.0

    val isCritical = onHand == 0.0
    val chipColor = if (isCritical) Color(0xFFDC2626) else Color(0xFFF59E0B)
    val chipLabel = if (isCritical) "Critical" else "Low"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.part.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                item.part.sku?.let { sku ->
                    Text(
                        text = "SKU: $sku",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "On hand: ${onHand.toLong()}  •  Reorder at: ${reorderQty.toLong()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = chipColor.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = chipLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
