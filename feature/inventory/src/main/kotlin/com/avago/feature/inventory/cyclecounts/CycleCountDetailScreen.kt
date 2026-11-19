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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.CycleCountState
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountDetailScreen(
    countId: String,
    onBack: () -> Unit,
    onFloorScan: () -> Unit = {},
    viewModel: CycleCountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val count = state.count
    val countState = state.state

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            text = { Text(stringResource(R.string.cycle_count_cancel_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) { Text(stringResource(R.string.cycle_count_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.common_dismiss)) }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(count?.number ?: count?.id?.take(8) ?: stringResource(R.string.cycle_count_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (countState == CycleCountState.COUNTING && state.lines.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onFloorScan,
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    text = { Text(stringResource(R.string.cycle_count_floor_fab)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                )
            }
        },
    ) { padding ->
        if (count == null || countState == null) {
            EmptyState(
                message = stringResource(if (state.isLoading) R.string.common_loading else R.string.cycle_count_empty),
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
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(state.location?.name ?: count.locationId.take(8), style = MaterialTheme.typography.titleMedium)
                        state.location?.addressSummary?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cycleCountStatusLabel(countState),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        count.scheduledOn?.let {
                            Text(
                                "${stringResource(R.string.cycle_count_scheduled_on)}: $it",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        count.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (state.lines.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.cycle_count_progress,
                                    state.lines.count { it.line.countedQty != null },
                                    state.lines.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.lines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (countState == CycleCountState.PLANNED || countState == CycleCountState.CANCELLED) {
                                R.string.cycle_count_no_lines
                            } else {
                                R.string.cycle_count_waiting_lines
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.lines, key = { it.line.id }) { item ->
                    CycleCountLineRow(
                        item = item,
                        isEditable = countState == CycleCountState.COUNTING,
                        showExpected = state.showExpected,
                        onQtyChange = { qty -> viewModel.setLineQty(item.line.id, qty) },
                        onSave = { viewModel.saveLine(item.line.id) },
                    )
                    HorizontalDivider()
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.actionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    when (countState) {
                        CycleCountState.PLANNED -> Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cycle_count_start))
                        }
                        CycleCountState.COUNTING -> Button(
                            onClick = viewModel::complete,
                            enabled = state.lines.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.cc_complete_btn)) }
                        CycleCountState.COUNTED -> Button(onClick = viewModel::post, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cycle_count_post))
                        }
                        CycleCountState.POSTED, CycleCountState.CANCELLED -> Unit
                    }
                    if (countState != CycleCountState.POSTED && countState != CycleCountState.CANCELLED) {
                        OutlinedButton(
                            onClick = { confirmCancel = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.cycle_count_cancel)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleCountLineRow(
    item: CycleCountLineItem,
    isEditable: Boolean,
    showExpected: Boolean,
    onQtyChange: (String) -> Unit,
    onSave: () -> Unit,
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
            Text(text = line.productName ?: line.productSku ?: line.productId.take(8), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showExpected) {
                    line.expectedQty?.let {
                        Text(
                            text = "${stringResource(R.string.cycle_count_expected)}: ${it.plain()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.variance?.let {
                        Text(
                            text = "${stringResource(R.string.cycle_count_variance)}: ${it.plain()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.signum() != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (isEditable) {
            OutlinedTextField(
                value = item.input,
                onValueChange = onQtyChange,
                label = { Text(stringResource(R.string.cycle_count_counted)) },
                modifier = Modifier.weight(0.45f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
            IconButton(onClick = onSave, enabled = item.isDirty) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cycle_count_save_line))
            }
        } else {
            Text(text = line.countedQty?.plain() ?: "—", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
