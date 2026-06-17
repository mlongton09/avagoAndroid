package com.avago.feature.inventory.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.network.model.PartBinLocation
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartDetailScreen(
    partId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    viewModel: PartDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showReceiveSheet) {
        state.inventory?.let { inventory ->
            ReceiveUseModalSheet(
                inventoryId = inventory.inventoryId,
                mode = ReceiveUseMode.RECEIVE,
                onDismiss = viewModel::dismissSheet,
            )
        }
    }
    if (state.showUseSheet) {
        state.inventory?.let { inventory ->
            ReceiveUseModalSheet(
                inventoryId = inventory.inventoryId,
                mode = ReceiveUseMode.USE,
                onDismiss = viewModel::dismissSheet,
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.part?.name ?: stringResource(R.string.part_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.part_edit))
                    }
                },
            )
        },
    ) { padding ->
        val part = state.part
        if (part == null) {
            EmptyState(
                message = stringResource(R.string.common_loading),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Part header card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        part.sku?.let { LabelValue(stringResource(R.string.part_sku), it) }
                        // Part number (sku shown as "Part #")
                        if (!part.sku.isNullOrBlank()) {
                            Text(
                                text = "Part #: ${part.sku}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Manufacturer from attributes JSON
                        if (!state.manufacturer.isNullOrBlank()) {
                            Text(
                                text = "Mfr: ${state.manufacturer}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        part.category?.let { LabelValue(stringResource(R.string.part_category), it) }
                        part.description?.let { LabelValue("Description", it) }
                    }
                }
            }

            item {
                // Stock stats card with inline quick-adjust buttons
                val inv = state.inventory
                val sl = state.stockingLevel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.part_stock_stats),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        LabelValue(
                            stringResource(R.string.part_on_hand),
                            "%.2f".format(inv?.quantityOnHand ?: 0.0),
                        )
                        sl?.minQty?.let { LabelValue(stringResource(R.string.part_min_qty), "%.2f".format(it)) }
                        sl?.maxQty?.let { LabelValue(stringResource(R.string.part_max_qty), "%.2f".format(it)) }
                        sl?.reorderQty?.let { LabelValue(stringResource(R.string.part_reorder_point), "%.2f".format(it)) }
                        sl?.safetyStock?.let { LabelValue("Safety Stock", "%.2f".format(it)) }
                        // Quick-adjust inline buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.showReceiveSheet() },
                                modifier = Modifier.weight(1f),
                                enabled = inv != null,
                            ) { Text("+ Receive") }
                            OutlinedButton(
                                onClick = { viewModel.showUseSheet() },
                                modifier = Modifier.weight(1f),
                                enabled = inv != null,
                            ) { Text("− Use") }
                        }
                    }
                }
            }

            item {
                // Primary action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::openReceive,
                        modifier = Modifier.weight(1f),
                        enabled = state.inventory != null,
                    ) {
                        Text(stringResource(R.string.part_receive))
                    }
                    OutlinedButton(
                        onClick = viewModel::openUse,
                        modifier = Modifier.weight(1f),
                        enabled = state.inventory != null,
                    ) {
                        Text(stringResource(R.string.part_use))
                    }
                }
            }

            item {
                // Transaction History Card
                TransactionHistoryCard(transactions = state.partTransactions)
            }

            // Change 144: Bin locations — "Where is it?" section
            item {
                BinLocationsCard(
                    bins = state.binLocations,
                    isLoading = state.binLocationsLoading,
                )
            }

            item {
                // Vendor Sources Card
                VendorSourcesCard(sources = state.vendorSources)
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// Change 144: "Where is it?" — bin locations card
@Composable
private fun BinLocationsCard(
    bins: List<PartBinLocation>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Locations",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                bins.isEmpty() -> {
                    Text(
                        text = "Not assigned to any bin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    bins.forEach { bin ->
                        val displayName = bin.bin_name
                            ?: bin.bin_label
                            ?: bin.bin_id
                        val location = bin.location_path?.takeIf { it.isNotBlank() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, style = MaterialTheme.typography.bodyMedium)
                                if (location != null) {
                                    Text(
                                        text = location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = "qty: ${bin.quantity.toLong()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorSourcesCard(sources: List<VendorSource>) {
    if (sources.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Vendor Sources", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            sources.forEach { src ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(src.vendorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (src.isPreferred) {
                                Spacer(Modifier.width(4.dp))
                                Text("⭐", fontSize = 12.sp)
                            }
                        }
                        src.sku?.let { Text("SKU: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        src.unitCost?.let { Text("${'$'}${String.format("%.2f", it)}", style = MaterialTheme.typography.bodyMedium) }
                        src.leadDays?.let { Text("$it day lead", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
                    }
                }
            }
        }
    }
}
