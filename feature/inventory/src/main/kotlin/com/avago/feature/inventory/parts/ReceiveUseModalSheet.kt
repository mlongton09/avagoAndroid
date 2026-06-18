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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveUseModalSheet(
    inventoryId: String,
    mode: ReceiveUseMode,
    onDismiss: () -> Unit,
    viewModel: ReceiveUseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDismiss()
    }

    // Change 50: reason dropdown state
    var reasonExpanded by remember { mutableStateOf(false) }

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

            // Change 50: Reason code dropdown
            Box {
                OutlinedTextField(
                    value = state.reasonCode?.replace("_", " ") ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Reason") },
                    placeholder = { Text("Select reason (optional)") },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    },
                    isError = state.error != null && state.reasonCode == "ADJUSTMENT",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                // Invisible click target over the field to open dropdown
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .semantics { role = Role.Button }
                        .clickable { reasonExpanded = true },
                )
                DropdownMenu(
                    expanded = reasonExpanded,
                    onDismissRequest = { reasonExpanded = false },
                ) {
                    INVENTORY_REASON_CODES.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code.replace("_", " ")) },
                            onClick = {
                                viewModel.setReasonCode(code)
                                reasonExpanded = false
                            },
                        )
                    }
                }
            }
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
                onClick = { viewModel.submit(inventoryId, mode) },
                enabled = !state.isSubmitting,
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
