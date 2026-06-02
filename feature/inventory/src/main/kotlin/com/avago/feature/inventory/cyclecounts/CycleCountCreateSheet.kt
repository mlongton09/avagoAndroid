package com.avago.feature.inventory.cyclecounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountCreateSheet(
    onDismiss: () -> Unit,
    onCreatedAndStarted: (String) -> Unit,
    viewModel: CreateCycleCountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.createdCountId) {
        state.createdCountId?.let(onCreatedAndStarted)
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
            Text(stringResource(R.string.cycle_count_create_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.locationId,
                onValueChange = viewModel::setLocationId,
                label = { Text(stringResource(R.string.cycle_count_location_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.cycle_count_scope_label), style = MaterialTheme.typography.labelMedium)
            listOf(
                "full" to stringResource(R.string.cycle_count_scope_full),
                "zone" to stringResource(R.string.cycle_count_scope_zone),
                "category" to stringResource(R.string.cycle_count_scope_category),
            ).forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.scopeType == value,
                        onClick = { viewModel.setScopeType(value) },
                    )
                    Text(label)
                }
            }

            if (state.scopeType != "full") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.scopeValue,
                    onValueChange = viewModel::setScopeValue,
                    label = { Text(if (state.scopeType == "zone") "Zone" else "Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

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
                    if (state.isSubmitting) stringResource(R.string.cycle_count_submitting)
                    else stringResource(R.string.cc_create_btn),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
