package com.avago.feature.inventory.parts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.ProductSupplier
import com.avago.core.data.domain.StockCondition
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.StockingPolicy
import com.avago.core.data.domain.formatMoney
import com.avago.core.data.domain.formatQty
import com.avago.core.ui.CustomFieldsCard
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

    state.sheet?.let { mode ->
        ReceiveUseModalSheet(productId = partId, mode = mode, onDismiss = viewModel::dismissSheet)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.product?.name ?: stringResource(R.string.part_detail_title)) },
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
        val product = state.product
        if (product == null) {
            EmptyState(
                message = stringResource(if (state.isLoading) R.string.common_loading else R.string.part_not_found),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val locationNames = state.positions.associate { it.locationId to (it.locationName ?: it.locationId) }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LabelValue(stringResource(R.string.part_sku), product.sku)
                        product.manufacturer?.takeIf { it.isNotBlank() }?.let {
                            LabelValue("Manufacturer", listOfNotNull(it, product.modelNumber).joinToString(" · "))
                        }
                        product.categoryName?.let { LabelValue(stringResource(R.string.part_category), it) }
                        product.gtin?.let { LabelValue(stringResource(R.string.part_barcode_label), it) }
                        product.standardUnitCost?.let {
                            LabelValue(stringResource(R.string.part_unit_cost_label), it.formatMoney(product.costCurrency))
                        }
                        product.notes?.takeIf { it.isNotBlank() }?.let { LabelValue("Notes", it) }
                    }
                }
            }

            item {
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
                        LabelValue(stringResource(R.string.part_on_hand), "${state.onHand.formatQty()} ${product.uomCode}")
                        state.policies.forEach { policy ->
                            PolicyRows(policy, locationName = locationNames[policy.locationId], showLocation = state.policies.size > 1)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::openReceive,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.part_receive))
                    }
                    OutlinedButton(
                        onClick = viewModel::openUse,
                        modifier = Modifier.weight(1f),
                        enabled = state.positions.any { it.onHand.signum() > 0 },
                    ) {
                        Text(stringResource(R.string.part_use))
                    }
                }
            }

            item { PositionsCard(state.positions) }

            item { TransactionHistoryCard(transactions = state.history) }

            item { SuppliersCard(state.suppliers) }

            item { CustomFieldsCard(entity = CustomFieldEntity.PRODUCT, entityId = product.id) }
        }
    }
}

@Composable
private fun PolicyRows(policy: StockingPolicy, locationName: String?, showLocation: Boolean) {
    if (showLocation) {
        Text(
            text = locationName ?: "—",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    policy.minQty?.let { LabelValue(stringResource(R.string.part_min_qty), it.formatQty()) }
    policy.maxQty?.let { LabelValue(stringResource(R.string.part_max_qty), it.formatQty()) }
    if (policy.minQty != null || policy.safetyQty != null) {
        LabelValue(stringResource(R.string.part_reorder_point), policy.reorderPoint.formatQty())
    }
    policy.safetyQty?.let { LabelValue(stringResource(R.string.part_safety_stock_label), it.formatQty()) }
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

/** "Where is it?" — every location/bin holding the product. */
@Composable
private fun PositionsCard(positions: List<StockPosition>, modifier: Modifier = Modifier) {
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
                Text(text = "Locations", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            if (positions.isEmpty()) {
                Text(
                    text = "Not stocked anywhere",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            positions.forEach { position ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(position.binCode ?: position.locationName ?: position.locationId, style = MaterialTheme.typography.bodyMedium)
                        val detail = listOfNotNull(
                            position.locationName.takeIf { position.binCode != null },
                            position.condition.takeIf { it != StockCondition.GOOD }?.wire?.replace('_', ' '),
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = position.onHand.formatQty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuppliersCard(suppliers: List<ProductSupplier>) {
    if (suppliers.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Vendor Sources", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            suppliers.forEach { src ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(src.supplierName ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (src.isPreferred) {
                                Spacer(Modifier.width(4.dp))
                                Text("⭐", fontSize = 12.sp)
                            }
                        }
                        src.supplierSku?.let {
                            Text("SKU: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        src.unitCost?.let { Text(it.formatMoney(src.currency), style = MaterialTheme.typography.bodyMedium) }
                        src.leadTimeDays?.let {
                            Text("$it day lead", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
