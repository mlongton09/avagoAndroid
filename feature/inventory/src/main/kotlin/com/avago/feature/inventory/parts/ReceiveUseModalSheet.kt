package com.avago.feature.inventory.parts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.StockPosition
import com.avago.core.data.domain.formatQty
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveUseModalSheet(
    productId: String,
    mode: ReceiveUseMode,
    onDismiss: () -> Unit,
    viewModel: ReceiveUseViewModel = hiltViewModel(),
) {
    LaunchedEffect(productId, mode) { viewModel.start(productId, mode) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(
                    if (mode == ReceiveUseMode.RECEIVE) R.string.receive_use_title_receive
                    else R.string.receive_use_title_use,
                ),
            )
            Spacer(Modifier.height(16.dp))

            when (mode) {
                ReceiveUseMode.RECEIVE -> {
                    SelectField(
                        label = stringResource(R.string.part_location),
                        value = state.selectedLocation?.name.orEmpty(),
                        options = state.locations.map { it.id to it.name },
                        onSelect = viewModel::selectLocation,
                    )
                    if (state.bins.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SelectField(
                            label = stringResource(R.string.part_bin),
                            value = state.selectedBin?.label ?: "—",
                            options = listOf("" to "—") + state.bins.map { it.id to it.label },
                            onSelect = { viewModel.selectBin(it.ifEmpty { null }) },
                        )
                    }
                }
                ReceiveUseMode.USE -> {
                    if (state.positions.isEmpty()) {
                        Text(
                            text = "No stock on hand",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        SelectField(
                            label = stringResource(R.string.part_location),
                            value = state.selectedPosition?.let(::positionLabel).orEmpty(),
                            options = state.positions.map { it.id to positionLabel(it) },
                            onSelect = viewModel::selectPosition,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.quantity,
                onValueChange = viewModel::setQuantity,
                label = { Text(stringResource(R.string.receive_use_quantity_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error != null,
            )
            Spacer(Modifier.height(12.dp))

            SelectField(
                label = "Reason",
                value = state.reasonCode?.replace("_", " ").orEmpty(),
                placeholder = "Select reason (optional)",
                options = INVENTORY_REASON_CODES.map { it to it.replace("_", " ") },
                onSelect = viewModel::setReasonCode,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.receive_use_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.receive_use_error, it),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting && (mode == ReceiveUseMode.RECEIVE || state.positions.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) stringResource(R.string.receive_use_submitting)
                    else stringResource(R.string.receive_use_submit),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun positionLabel(position: StockPosition): String =
    listOfNotNull(position.locationName ?: position.locationId, position.binCode).joinToString(" · ") +
        " (${position.onHand.formatQty()})"

/** A read-only field that opens a menu of [options] (id to label). */
@Composable
internal fun SelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    placeholder: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { role = Role.Button }
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
