package com.avago.feature.inventory.purchaseorders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.PurchaseOrder
import com.avago.core.data.domain.PurchaseOrderStatus
import com.avago.core.data.v2.V2Money
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderListScreen(
    onPoClick: (String) -> Unit,
    onCreatePo: () -> Unit,
    viewModel: PurchaseOrderListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.po_list_title)) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.po_create))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Status filter chips
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
                    items(state.filtered, key = { it.id }) { po ->
                        PoCard(po = po, onClick = { onPoClick(po.id) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun PoCard(po: PurchaseOrder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = po.displayNumber,
                    style = MaterialTheme.typography.titleMedium,
                )
                po.supplierName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(poStatusLabel(po.status), style = MaterialTheme.typography.labelSmall) },
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPoMoney(po.currency, po.grandTotal),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun poStatusLabel(status: PurchaseOrderStatus): String = stringResource(
    when (status) {
        PurchaseOrderStatus.DRAFT -> R.string.po_status_draft
        PurchaseOrderStatus.PENDING_APPROVAL -> R.string.po_status_pending_approval
        PurchaseOrderStatus.APPROVED -> R.string.po_status_approved
        PurchaseOrderStatus.ORDERED -> R.string.po_status_ordered
        PurchaseOrderStatus.PARTIALLY_RECEIVED -> R.string.po_status_partially_received
        PurchaseOrderStatus.RECEIVED -> R.string.po_status_received
        PurchaseOrderStatus.CLOSED -> R.string.po_status_closed
        PurchaseOrderStatus.CANCELLED -> R.string.po_status_cancelled
    },
)

internal val PurchaseOrder.displayNumber: String get() = number ?: id.take(8)

/** Formats a money amount in the currency's minor-unit precision. */
internal fun formatPoMoney(currency: String, amount: BigDecimal): String =
    "$currency ${amount.setScale(V2Money.fractionDigits(currency), RoundingMode.HALF_UP).toPlainString()}"
