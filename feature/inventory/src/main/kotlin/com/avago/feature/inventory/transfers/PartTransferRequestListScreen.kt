package com.avago.feature.inventory.transfers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.PartTransferRequestEntity
import com.avago.feature.inventory.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartTransferRequestListScreen(
    onBack: () -> Unit,
    viewModel: PartTransferListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }

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
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.transfer_requests_create))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val requests = viewModel.filteredRequests()

        Column(modifier = Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.filterStatus == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text(stringResource(R.string.transfer_filter_all)) },
                    )
                }
                listOf("pending", "approved", "rejected", "cancelled").forEach { status ->
                    item {
                        FilterChip(
                            selected = state.filterStatus == status,
                            onClick = { viewModel.setFilter(status) },
                            label = { Text(status.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            if (requests.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.transfer_requests_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(requests, key = { it.requestId }) { req ->
                        TransferRequestCard(req = req)
                    }
                }
            }
        }

        if (showCreateSheet) {
            CreatePartTransferRequestSheet(onDismiss = { showCreateSheet = false })
        }
    }
}

@Composable
private fun TransferRequestCard(req: PartTransferRequestEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (badgeColor, textColor) = statusColors(req.status)
                    Badge(containerColor = badgeColor, contentColor = textColor) {
                        Text(
                            req.status.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Qty: ${req.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val locationLine = listOfNotNull(
                    req.fromLocationId?.let { "From: $it" },
                    req.toLocationId?.let { "To: $it" },
                ).joinToString("  ")
                if (locationLine.isNotBlank()) {
                    Text(locationLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateFmt.format(Date(req.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (req.notes != null) {
                    Text(req.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun statusColors(status: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        "approved" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "rejected", "cancelled" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}
