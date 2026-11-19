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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.CycleCountState
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountListScreen(
    onCountClick: (String) -> Unit,
    onCreateStartedCount: (String) -> Unit = onCountClick,
    viewModel: CycleCountListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showCreateSheet) {
        CycleCountCreateSheet(
            onDismiss = viewModel::dismissCreate,
            onCreatedAndStarted = { countId ->
                viewModel.dismissCreate()
                onCreateStartedCount(countId)
            },
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.cycle_count_list_title)) }) },
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
        CycleCountListContent(
            state = state,
            onStatus = viewModel::setStatus,
            onCountClick = onCountClick,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Status filter chips plus the count list; shared with the purchasing container's tab. */
@Composable
internal fun CycleCountListContent(
    state: CycleCountListUiState,
    onStatus: (CycleCountState?) -> Unit,
    onCountClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedStatus == null,
                    onClick = { onStatus(null) },
                    label = { Text(stringResource(R.string.po_status_all)) },
                )
            }
            items(CYCLE_COUNT_STATUSES) { status ->
                FilterChip(
                    selected = state.selectedStatus == status,
                    onClick = { onStatus(status) },
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
                items(state.filtered, key = { it.count.id }) { item ->
                    CycleCountCard(item = item, onClick = { onCountClick(item.count.id) })
                }
            }
        }
    }
}

@Composable
private fun CycleCountCard(item: CycleCountListItem, onClick: () -> Unit) {
    val count = item.count
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = count.number ?: count.id.take(8), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.locationName ?: count.locationId.take(8),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (count.lines.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.cycle_count_progress, item.counted, count.lines.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AssistChip(onClick = {}, label = { Text(cycleCountStatusLabel(count.countState)) })
        }
    }
}

@Composable
fun cycleCountStatusLabel(status: CycleCountState): String = stringResource(
    when (status) {
        CycleCountState.PLANNED -> R.string.cycle_count_status_planned
        CycleCountState.COUNTING -> R.string.cycle_count_status_counting
        CycleCountState.COUNTED -> R.string.cycle_count_status_counted
        CycleCountState.POSTED -> R.string.cycle_count_status_posted
        CycleCountState.CANCELLED -> R.string.cycle_count_status_cancelled
    },
)
