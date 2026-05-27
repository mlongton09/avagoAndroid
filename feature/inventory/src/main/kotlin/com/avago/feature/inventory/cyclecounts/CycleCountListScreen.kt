package com.avago.feature.inventory.cyclecounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountListScreen(
    onCountClick: (String) -> Unit,
    viewModel: CycleCountListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showCreateSheet) {
        CycleCountCreateSheet(onDismiss = viewModel::dismissCreate)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.cycle_count_list_title)) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cycle_count_create))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Status filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedStatus == null,
                        onClick = { viewModel.setStatus(null) },
                        label = { Text(stringResource(R.string.po_status_all)) },
                    )
                }
                items(CYCLE_COUNT_STATUSES) { status ->
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { viewModel.setStatus(status) },
                        label = { Text(cycleCountStatusLabel(status)) },
                    )
                }
            }

            if (state.filtered.isEmpty() && !state.isLoading) {
                EmptyState(message = stringResource(R.string.cycle_count_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filtered, key = { it.cycleCountId }) { count ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCountClick(count.cycleCountId) },
                            elevation = CardDefaults.cardElevation(1.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = count.cycleCountId.take(8),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = count.locationId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = { Text(cycleCountStatusLabel(count.status)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun cycleCountStatusLabel(status: String): String = when (status) {
    "in_progress" -> stringResource(R.string.cycle_count_status_in_progress)
    "locked" -> stringResource(R.string.cycle_count_status_locked)
    "reconciled" -> stringResource(R.string.cycle_count_status_reconciled)
    else -> status
}
