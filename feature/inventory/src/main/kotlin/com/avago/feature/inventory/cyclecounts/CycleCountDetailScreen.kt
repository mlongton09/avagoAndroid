package com.avago.feature.inventory.cyclecounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountDetailScreen(
    countId: String,
    onBack: () -> Unit,
    viewModel: CycleCountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.count?.cycleCountId?.take(8) ?: stringResource(R.string.cycle_count_detail_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        val count = state.count
        if (count == null) {
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
                // Location header card
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        state.location?.let { loc ->
                            Text(loc.name, style = MaterialTheme.typography.titleMedium)
                            loc.address?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } ?: Text(count.locationId, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cycleCountStatusLabel(count.status),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.cycle_count_detail_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.lines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cycle_count_no_lines),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.lines, key = { it.line.lineId }) { item ->
                    CycleCountLineRow(
                        item = item,
                        isEditable = count.status == "in_progress",
                        onQtyChange = { qty -> viewModel.setLineQty(item.line.lineId, qty) },
                    )
                    HorizontalDivider()
                }
            }

            item {
                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.actionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    when (count.status) {
                        "in_progress" -> Button(
                            onClick = viewModel::lock,
                            enabled = !state.isActioning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.isActioning) stringResource(R.string.cycle_count_locking)
                                else stringResource(R.string.cycle_count_lock),
                            )
                        }
                        "locked" -> Button(
                            onClick = viewModel::reconcile,
                            enabled = !state.isActioning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.isActioning) stringResource(R.string.cycle_count_reconciling)
                                else stringResource(R.string.cycle_count_reconcile),
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleCountLineRow(
    item: CycleCountLineWithPart,
    isEditable: Boolean,
    onQtyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = item.line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.part?.name ?: line.partId ?: "—",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "${stringResource(R.string.cycle_count_expected)}: ${"%.2f".format(line.expectedQty ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                line.variance?.let {
                    Text(
                        text = "${stringResource(R.string.cycle_count_variance)}: ${"%.2f".format(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it != 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isEditable) {
            OutlinedTextField(
                value = item.countedQtyInput,
                onValueChange = onQtyChange,
                label = { Text(stringResource(R.string.cycle_count_counted)) },
                modifier = Modifier.weight(0.45f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        } else {
            Text(
                text = "${"%.2f".format(line.countedQty ?: 0.0)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
