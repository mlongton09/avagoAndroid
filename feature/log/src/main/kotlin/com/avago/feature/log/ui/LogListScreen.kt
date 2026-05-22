package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.data.db.entity.LogEntity
import com.avago.feature.log.viewmodel.LogListViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(
    assetId: String? = null,
    onLogClick: (entryId: String) -> Unit,
    onAddLog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogListViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) {
        viewModel.setAssetId(assetId)
    }

    val logs by viewModel.logs.collectAsState()
    val categories by viewModel.availableCategories.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Group logs by year for sticky headers
    val grouped = logs.groupBy { log ->
        Calendar.getInstance().apply { timeInMillis = log.entryDate }.get(Calendar.YEAR)
    }.toSortedMap(reverseOrder())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (assetId != null) "Log Entries" else "All Logs") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLog) {
                Icon(Icons.Default.Add, contentDescription = "Add log entry")
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column {
                // Category filter pills
                if (categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = categoryFilter == null,
                                onClick = { viewModel.setFilter(null) },
                                label = { Text("All") },
                            )
                        }
                        items(categories) { cat ->
                            FilterChip(
                                selected = categoryFilter == cat,
                                onClick = { viewModel.setFilter(if (categoryFilter == cat) null else cat) },
                                label = { Text(cat) },
                            )
                        }
                    }
                }

                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No log entries yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (year, yearLogs) ->
                            // Sticky year header
                            stickyHeader(key = "year_$year") {
                                YearHeader(year = year)
                            }
                            items(yearLogs, key = { it.entryId }) { log ->
                                LogListRow(
                                    log = log,
                                    onClick = { onLogClick(log.entryId) },
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
private fun YearHeader(year: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogListRow(
    log: LogEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category icon
        Icon(
            imageVector = categoryIcon(log.category),
            contentDescription = log.category,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))

        // Title + category + date
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (log.category != null) {
                Text(
                    text = log.category!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = dateFormatter.format(Date(log.entryDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Cost + status
        Column(horizontalAlignment = Alignment.End) {
            val displayCost = log.cost
            if (displayCost != null && displayCost > 0) {
                Text(
                    text = currencyFormat.format(displayCost),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            // Sync status indicator: show dot if not yet synced (serverVersion == 0 and seq == null)
            if (log.serverVersion == 0L && log.seq == null) {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun categoryIcon(category: String?) = when {
    category == null -> Icons.Default.Build
    category.contains("inspect", ignoreCase = true) -> Icons.Default.CheckCircle
    category.contains("fuel", ignoreCase = true) -> Icons.Default.LocalGasStation
    category.contains("note", ignoreCase = true) -> Icons.Default.Note
    else -> Icons.Default.Build
}
