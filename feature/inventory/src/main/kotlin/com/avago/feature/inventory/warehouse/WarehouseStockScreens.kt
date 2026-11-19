package com.avago.feature.inventory.warehouse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.formatQty
import com.avago.feature.inventory.R
import com.avago.feature.inventory.parts.SelectField

@Composable
fun WarehouseReceiveScreen(onBack: () -> Unit, viewModel: WarehouseReceiveViewModel = hiltViewModel()) =
    WarehouseStockScreen(R.string.warehouse_receive_title, onBack, viewModel)

@Composable
fun WarehouseIssueScreen(onBack: () -> Unit, viewModel: WarehouseIssueViewModel = hiltViewModel()) =
    WarehouseStockScreen(R.string.warehouse_issue_title, onBack, viewModel)

@Composable
fun WarehouseMoveScreen(onBack: () -> Unit, viewModel: WarehouseMoveViewModel = hiltViewModel()) =
    WarehouseStockScreen(R.string.warehouse_move_title, onBack, viewModel)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseStockScreen(
    title: Int,
    onBack: () -> Unit,
    viewModel: WarehouseStockViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.form.isDone) {
        if (state.form.isDone) onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.form.partSearch,
                onValueChange = viewModel::setPartSearch,
                label = { Text(stringResource(R.string.warehouse_part_label)) },
                placeholder = { Text(stringResource(R.string.warehouse_part_search)) },
                supportingText = state.selectedProduct?.let { { Text(it.sku) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            state.matchingProducts.forEach { product ->
                Text(
                    text = "${product.name} · ${product.sku}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectProduct(product) }
                        .padding(8.dp),
                )
                HorizontalDivider()
            }

            if (state.needsSource && state.selectedProduct != null) {
                if (state.positions.isEmpty()) {
                    Text("No stock on hand", color = MaterialTheme.colorScheme.error)
                } else {
                    SelectField(
                        label = stringResource(R.string.warehouse_from_location),
                        value = state.fromPosition?.let(::positionLabel).orEmpty(),
                        options = state.positions.map { it.id to positionLabel(it) },
                        onSelect = viewModel::selectFromPosition,
                    )
                }
            }

            if (state.needsDestination) {
                SelectField(
                    label = stringResource(
                        if (state.needsSource) R.string.warehouse_to_location else R.string.warehouse_location_label,
                    ),
                    value = state.toLocation?.name.orEmpty(),
                    options = state.locations.map { it.id to it.name },
                    onSelect = viewModel::selectToLocation,
                )
                if (state.toBins.isNotEmpty()) {
                    SelectField(
                        label = stringResource(if (state.needsSource) R.string.warehouse_to_bin else R.string.warehouse_bin_label),
                        value = state.toBin?.label ?: "—",
                        options = listOf("" to "—") + state.toBins.map { it.id to it.label },
                        onSelect = { viewModel.selectToBin(it.ifEmpty { null }) },
                    )
                }
            }

            OutlinedTextField(
                value = state.form.quantity,
                onValueChange = viewModel::setQuantity,
                label = { Text(stringResource(R.string.warehouse_quantity_label)) },
                suffix = state.selectedProduct?.let { { Text(it.uomCode) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            OutlinedTextField(
                value = state.form.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.warehouse_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            state.form.error?.let {
                Text(stringResource(R.string.warehouse_error, it), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !state.form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.form.isSubmitting) stringResource(R.string.warehouse_submitting)
                    else stringResource(R.string.warehouse_submit),
                )
            }
        }
    }
}

private fun positionLabel(position: StockPosition): String =
    listOfNotNull(position.locationName ?: position.locationId, position.binCode).joinToString(" · ") +
        " (${position.onHand.formatQty()})"
