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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderListScreen(
    onPoClick: (String) -> Unit,
    onCreatePo: () -> Unit,
    viewModel: PurchaseOrderListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.po_list_title)) }) },
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
                    items(state.filtered, key = { it.po.poId }) { item ->
                        PoCard(item = item, onClick = { onPoClick(item.po.poId) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun PoCard(item: PoListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                    text = item.po.poNumber ?: item.po.poId.take(8),
                    style = MaterialTheme.typography.titleMedium,
                )
                item.vendor?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(poStatusLabel(item.po.status), style = MaterialTheme.typography.labelSmall) },
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                item.po.grandTotal?.let {
                    Text(
                        text = "${item.po.currency ?: "USD"} ${"%.2f".format(it)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun poStatusLabel(status: String): String = when (status) {
    "draft" -> stringResource(R.string.po_status_draft)
    "pending_approval" -> stringResource(R.string.po_status_pending_approval)
    "approved" -> stringResource(R.string.po_status_approved)
    "ordered" -> stringResource(R.string.po_status_ordered)
    "partially_received" -> stringResource(R.string.po_status_partially_received)
    "received" -> stringResource(R.string.po_status_received)
    "closed" -> stringResource(R.string.po_status_closed)
    "cancelled" -> stringResource(R.string.po_status_cancelled)
    else -> status
}
