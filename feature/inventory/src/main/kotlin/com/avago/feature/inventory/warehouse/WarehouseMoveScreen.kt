package com.avago.feature.inventory.warehouse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R
import com.avago.feature.inventory.ui.LocationPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseMoveScreen(
    onBack: () -> Unit,
    viewModel: WarehouseMoveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showFromLocationPicker by remember { mutableStateOf(false) }
    var showToLocationPicker by remember { mutableStateOf(false) }

    if (showFromLocationPicker) {
        LocationPickerSheet(
            onLocationPicked = { location ->
                if (location != null) viewModel.setFromLocationId(location.locationId)
                showFromLocationPicker = false
            },
            onDismiss = { showFromLocationPicker = false },
        )
    }

    if (showToLocationPicker) {
        LocationPickerSheet(
            onLocationPicked = { location ->
                if (location != null) viewModel.setToLocationId(location.locationId)
                showToLocationPicker = false
            },
            onDismiss = { showToLocationPicker = false },
        )
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.warehouse_move_title)) },
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
                value = state.partSearch,
                onValueChange = viewModel::setPartSearch,
                label = { Text(stringResource(R.string.warehouse_part_label)) },
                placeholder = { Text(stringResource(R.string.warehouse_part_search)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            val filtered = viewModel.filteredParts()
            if (state.partSearch.isNotBlank() && state.selectedPart == null && filtered.isNotEmpty()) {
                LazyColumn(modifier = Modifier.height(160.dp)) {
                    items(filtered, key = { it.partId }) { part ->
                        Text(
                            text = part.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPart(part) }
                                .padding(8.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.quantity,
                onValueChange = viewModel::setQuantity,
                label = { Text(stringResource(R.string.warehouse_quantity_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            OutlinedButton(
                onClick = { showFromLocationPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.fromLocationId.isNotBlank()) state.fromLocationId
                    else stringResource(R.string.warehouse_from_location),
                )
            }

            OutlinedTextField(
                value = state.fromBinId,
                onValueChange = viewModel::setFromBinId,
                label = { Text(stringResource(R.string.warehouse_from_bin)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedButton(
                onClick = { showToLocationPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.toLocationId.isNotBlank()) state.toLocationId
                    else stringResource(R.string.warehouse_to_location),
                )
            }

            OutlinedTextField(
                value = state.toBinId,
                onValueChange = viewModel::setToBinId,
                label = { Text(stringResource(R.string.warehouse_to_bin)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.warehouse_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) stringResource(R.string.warehouse_submitting)
                    else stringResource(R.string.warehouse_submit),
                )
            }
        }
    }
}
