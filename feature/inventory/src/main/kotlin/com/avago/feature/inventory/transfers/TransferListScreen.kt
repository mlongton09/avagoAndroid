package com.avago.feature.inventory.transfers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.MovementStatus
import com.avago.feature.inventory.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferListScreen(
    onBack: () -> Unit,
    viewModel: TransferListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.transfer_requests_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedStatus == null,
                        onClick = { viewModel.setStatus(null) },
                        label = { Text(stringResource(R.string.transfer_filter_all)) },
                    )
                }
                items(TRANSFER_STATUSES) { status ->
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { viewModel.setStatus(status) },
                        label = { Text(transferStatusLabel(status)) },
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.transfer_requests_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.transfer.id }) { item ->
                        TransferCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(item: TransferListItem) {
    val transfer = item.transfer
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    transfer.number ?: stringResource(transferKindLabel(transfer.kind)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                val (badgeColor, textColor) = statusColors(transfer.status)
                Badge(containerColor = badgeColor, contentColor = textColor) {
                    Text(transferStatusLabel(transfer.status), modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            val route = listOfNotNull(
                item.fromName?.let { stringResource(R.string.transfer_from, it) },
                item.toName?.let { stringResource(R.string.transfer_to, it) },
            ).joinToString("  ")
            if (route.isNotBlank()) {
                Text(route, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            transfer.lines.forEach { line ->
                val qty = line.quantity ?: line.requestedQty
                Text(
                    "${qty?.stripTrailingZeros()?.toPlainString() ?: "\u2014"} \u00d7 ${line.productName ?: line.productSku ?: line.productId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                dateFmt.format(Date(transfer.effectiveAt ?: transfer.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (transfer.decisionNote ?: transfer.notes)?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

private fun transferKindLabel(kind: MovementKind): Int = when (kind) {
    MovementKind.TRANSFER_DISPATCH -> R.string.transfer_kind_dispatch
    MovementKind.TRANSFER_RECEIPT -> R.string.transfer_kind_receipt
    else -> R.string.part_issues_type_transfer
}

@Composable
private fun transferStatusLabel(status: MovementStatus): String = stringResource(
    when (status) {
        MovementStatus.DRAFT -> R.string.transfer_status_draft
        MovementStatus.REQUESTED -> R.string.transfer_status_requested
        MovementStatus.APPROVED -> R.string.transfer_status_approved
        MovementStatus.DECLINED -> R.string.transfer_status_declined
        MovementStatus.POSTED -> R.string.transfer_status_posted
        MovementStatus.CANCELLED -> R.string.transfer_status_cancelled
    },
)

@Composable
private fun statusColors(status: MovementStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        MovementStatus.APPROVED, MovementStatus.POSTED -> scheme.primaryContainer to scheme.onPrimaryContainer
        MovementStatus.DECLINED, MovementStatus.CANCELLED -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}
