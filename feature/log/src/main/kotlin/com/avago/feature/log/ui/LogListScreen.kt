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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.svg.SvgDecoder
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.ui.ScoutFAB
import com.avago.core.ui.ScoutViewModel
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
    scoutViewModel: ScoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) {
        viewModel.setAssetId(assetId)
    }

    val logs by viewModel.logs.collectAsState()
    val categories by viewModel.availableCategories.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoutFAB(onQuery = { query -> scoutViewModel.query(query) })
                FloatingActionButton(onClick = onAddLog) {
                    Icon(Icons.Default.Add, contentDescription = "Add log entry")
                }
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
        CategoryBadge(categoryId = log.category)
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            log.category?.let { category ->
                Text(
                    text = category,
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

        Column(horizontalAlignment = Alignment.End) {
            val displayCost = log.cost
            if (displayCost != null && displayCost > 0) {
                Text(
                    text = currencyFormat.format(displayCost),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
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

@Composable
private fun CategoryBadge(
    categoryId: String?,
    modifier: Modifier = Modifier,
) {
    val iconName = categoryIconName(categoryId)
    val bgColor = categoryBadgeColor(iconName)
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "file:///android_asset/icons/$iconName.svg",
            imageLoader = imageLoader,
            contentDescription = categoryId,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp),
        )
    }
}
