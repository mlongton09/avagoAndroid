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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.ProductStock
import com.avago.core.data.domain.ReorderSuggestion
import com.avago.core.data.domain.StockStatus
import com.avago.core.data.domain.formatQty
import com.avago.feature.inventory.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/** A product at or below its reorder point, with the server's suggested quantity if any. */
data class ReorderItem(
    val stock: ProductStock,
    val suggestedQty: BigDecimal?,
    /** The server suggestions behind [suggestedQty]. */
    val suggestionIds: List<String> = emptyList(),
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
    private val inventory: InventoryRepository,
    admin: AdminRepository,
) : ViewModel() {

    /** Dismissing suggestions needs `reorder_suggestion.convert_to_po`. */
    val canDismiss: StateFlow<Boolean> = admin.observeAllowed(DISMISS_PERMISSION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<ReorderUiState> = combine(
        inventory.observeProductStock(),
        inventory.observeReorderSuggestions(),
    ) { stock, suggestions ->
        val byProduct = suggestions.groupBy(ReorderSuggestion::productId)
        val items = stock
            .filter { it.needsReorder || it.product.id in byProduct }
            .map { s ->
                val list = byProduct[s.product.id].orEmpty()
                ReorderItem(
                    stock = s,
                    suggestedQty = list.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { acc, x -> acc + x.suggestedQty },
                    suggestionIds = list.map { it.id },
                )
            }
            .sortedBy { it.stock.product.name.lowercase() }
        ReorderUiState(items = items, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReorderUiState())

    /** Dismisses the product's open suggestions; it stays listed while it is below its reorder point. */
    fun dismiss(item: ReorderItem) {
        viewModelScope.launch {
            runCatching { item.suggestionIds.forEach { inventory.dismissReorderSuggestion(it) } }.onFailure { e ->
                Timber.e(e, "WarehouseReorderViewModel: dismiss failed")
                _error.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        const val DISMISS_PERMISSION = "reorder_suggestion.convert_to_po"
    }
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
    val canDismiss by viewModel.canDismiss.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val reorderItems = state.items
    val isLoading = state.isLoading

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.warehouse_reorder_card)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
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
                    contentDescription = stringResource(R.string.reorder_create_po),
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
                        text = stringResource(R.string.reorder_all_stocked),
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
                        ReorderItemCard(
                            item = item,
                            onDismissSuggestion = if (canDismiss && item.suggestionIds.isNotEmpty()) {
                                { viewModel.dismiss(item) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReorderItemCard(item: ReorderItem, onDismissSuggestion: (() -> Unit)? = null) {
    val product = item.stock.product
    val isCritical = item.stock.status == StockStatus.OUT
    val chipColor = if (isCritical) Color(0xFFDC2626) else Color(0xFFF59E0B)
    val chipLabel = stringResource(if (isCritical) R.string.reorder_critical else R.string.reorder_low)

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
                    text = stringResource(R.string.reorder_sku, product.sku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.reorder_on_hand, item.stock.onHand.formatQty()),
                        stringResource(R.string.reorder_at, item.stock.reorderPoint.formatQty()),
                        item.suggestedQty?.let { stringResource(R.string.reorder_suggested, it.formatQty()) },
                    ).joinToString("  \u2022  "),
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
            if (onDismissSuggestion != null) {
                IconButton(onClick = onDismissSuggestion) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.reorder_dismiss_suggestion))
                }
            }
        }
    }
}
