package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.StockMovement
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GrnListItem(
    val receipt: StockMovement,
    val poNumber: String?,
)

data class GrnListUiState(
    val items: List<GrnListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class GrnListViewModel @Inject constructor(
    inventory: InventoryRepository,
    orders: PurchaseOrderRepository,
) : ViewModel() {

    val uiState: StateFlow<GrnListUiState> = combine(
        inventory.observeMovements(setOf(MovementKind.RECEIPT)),
        orders.observePurchaseOrders(),
    ) { receipts, pos ->
        val numbers = pos.associate { it.id to it.displayNumber }
        GrnListUiState(
            items = receipts
                .map { GrnListItem(it, it.purchaseOrderId?.let(numbers::get)) }
                .sortedByDescending { it.receipt.effectiveAt ?: it.receipt.createdAt },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrnListUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrnListScreen(
    onBack: () -> Unit,
    /** Opens the purchase order a receipt belongs to. */
    onPoClick: (String) -> Unit,
    viewModel: GrnListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.grn_list_title)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState(
                    message = stringResource(R.string.grn_list_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.receipt.id }) { item ->
                        GrnCard(
                            item = item,
                            onClick = item.receipt.purchaseOrderId?.let { poId -> { onPoClick(poId) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrnCard(
    item: GrnListItem,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.receipt.number ?: item.receipt.id.take(8),
                style = MaterialTheme.typography.titleMedium,
            )
            item.poNumber?.let { poNumber ->
                Text(
                    text = "${stringResource(R.string.grn_po_label)}: $poNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${stringResource(R.string.grn_received_date_label2)}: ${(item.receipt.effectiveAt ?: item.receipt.createdAt).formatGrnDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Long.formatGrnDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))
