package com.avago.feature.inventory.vendors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailScreen(
    vendorId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    viewModel: VendorDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.vendor?.name ?: stringResource(R.string.vendor_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.vendor_edit))
                    }
                },
            )
        },
    ) { padding ->
        val vendor = state.vendor
        if (vendor == null) {
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        vendor.email?.let { DetailRow(stringResource(R.string.vendor_email), it) }
                        vendor.phone?.let { DetailRow(stringResource(R.string.vendor_phone), it) }
                        vendor.address?.let { DetailRow(stringResource(R.string.vendor_address), it) }
                        vendor.paymentTerms?.let { DetailRow(stringResource(R.string.vendor_payment_terms), it) }
                        vendor.taxId?.let { DetailRow(stringResource(R.string.vendor_tax_id), it) }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.sources_section),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.vendorParts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.vendor_parts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.vendorParts, key = { it.vendorPart.vendorPartId }) { entry ->
                    VendorPartRow(entry = entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun VendorPartRow(entry: VendorPartWithPart) {
    val vp = entry.vendorPart
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.part?.name ?: vp.vendorSku ?: vp.partId,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (vp.isPreferred) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.vendor_part_preferred),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            vp.vendorSku?.let {
                Text(
                    text = "${stringResource(R.string.vendor_part_sku)}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                vp.leadDays?.let {
                    Text(
                        text = "${stringResource(R.string.vendor_part_lead_days)}: $it d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                vp.moq?.let {
                    Text(
                        text = "${stringResource(R.string.vendor_part_moq)}: ${it.toLong()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        vp.unitCost?.let { cost ->
            Text(
                text = "${vp.currency ?: "USD"} ${"%.2f".format(cost)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
