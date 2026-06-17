package com.avago.feature.inventory.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePartTransferRequestSheet(
    onDismiss: () -> Unit,
    viewModel: CreateTransferRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(state.isDone) { if (state.isDone) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.transfer_requests_create), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

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
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
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
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.quantity,
                onValueChange = viewModel::setQuantity,
                label = { Text(stringResource(R.string.warehouse_quantity_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.fromLocationId,
                onValueChange = viewModel::setFromLocationId,
                label = { Text(stringResource(R.string.transfer_field_from_location)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.toLocationId,
                onValueChange = viewModel::setToLocationId,
                label = { Text(stringResource(R.string.transfer_field_to_location)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.warehouse_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) stringResource(R.string.warehouse_submitting)
                    else stringResource(R.string.transfer_requests_submit),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
