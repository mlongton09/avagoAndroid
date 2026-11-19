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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.ProductStock
import com.avago.core.data.domain.ReorderSuggestion
import com.avago.core.data.domain.StockStatus
import com.avago.core.data.domain.formatQty
import com.avago.feature.inventory.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/** A product at or below its reorder point, with the server's suggested quantity if any. */
data class ReorderItem(
    val stock: ProductStock,
    val suggestedQty: BigDecimal?,
)

data class ReorderUiState(
    val items: List<ReorderItem> = emptyList(),
    val isLoading: Boolean = true,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class WarehouseReorderViewModel @Inject constructor(
    inventory: InventoryRepository,
) : ViewModel() {

    val state: StateFlow<ReorderUiState> = combine(
        inventory.observeProductStock(),
        inventory.observeReorderSuggestions(),
    ) { stock, suggestions ->
        val suggested = suggestions.groupBy(ReorderSuggestion::productId)
            .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { acc, s -> acc + s.suggestedQty } }
        val items = stock
            .filter { it.needsReorder || it.product.id in suggested }
            .map { ReorderItem(it, suggested[it.product.id]) }
            .sortedBy { it.stock.product.name.lowercase() }
        ReorderUiState(items = items, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReorderUiState())
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reorderItems = state.items
    val isLoading = state.isLoading

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
        Box(
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
                    items(reorderItems, key = { it.stock.product.id }) { item ->
                        ReorderItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReorderItemCard(item: ReorderItem) {
    val product = item.stock.product
    val isCritical = item.stock.status == StockStatus.OUT
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
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "SKU: ${product.sku}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(
                        "On hand: ${item.stock.onHand.formatQty()}",
                        "Reorder at: ${item.stock.reorderPoint.formatQty()}",
                        item.suggestedQty?.let { "Suggested: ${it.formatQty()}" },
                    ).joinToString("  •  "),
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
