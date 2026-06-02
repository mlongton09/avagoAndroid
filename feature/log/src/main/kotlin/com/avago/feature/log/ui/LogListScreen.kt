package com.avago.feature.log.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.ui.CategoryBadge
import com.avago.core.ui.ScoutFAB
import com.avago.core.ui.ScoutViewModel
import com.avago.feature.log.viewmodel.LogListViewModel
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
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LogListViewModel = hiltViewModel(),
    scoutViewModel: ScoutViewModel = hiltViewModel(),
) {
    LaunchedEffect(assetId) {
        viewModel.setAssetId(assetId)
    }

    val logs by viewModel.logs.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val asset by viewModel.asset.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val entryCount by viewModel.entryCount.collectAsState()
    val lastServiceDate by viewModel.lastServiceDate.collectAsState()

    val grouped = logs.groupBy { log ->
        Calendar.getInstance().apply { timeInMillis = log.entryDate }.get(Calendar.YEAR)
    }.toSortedMap(reverseOrder())

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when {
                            assetId != null && asset != null -> asset!!.name
                            assetId != null -> "Log Entries"
                            else -> "All Logs"
                        },
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoutFAB(onQuery = { query -> scoutViewModel.query(query) })
                FloatingActionButton(
                    onClick = onAddLog,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
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
                // Asset header — only when launched from a specific asset and the asset
                // has finished loading. Mirrors iOS AssetDetailHeaderView.
                if (assetId != null && asset != null) {
                    AssetLogHeader(
                        asset = asset!!,
                        photos = photos,
                        entryCount = entryCount,
                        lastServiceDate = lastServiceDate,
                        onAddPhotoUri = { uri -> viewModel.addAssetPhoto(uri) },
                        onDeletePhoto = { id -> viewModel.deleteAssetPhoto(id) },
                        onSetCoverPhoto = { id -> viewModel.setCoverPhoto(id) },
                    )
                }

                if (categoryFilter != null) {
                    FilterBanner(
                        categoryFilter = categoryFilter,
                        onClear = { viewModel.setFilter(null) },
                    )
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
                                    onCategoryClick = { viewModel.setFilter(log.category) },
                                    currencyCode = currencyCode,
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
private fun FilterBanner(
    categoryFilter: String?,
    onClear: () -> Unit,
) {
    val label = categoryFilter?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: ""
    val dotColor = categoryBadgeColor(categoryIconName(categoryFilter))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClear() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = "Filtering by $label",
            style = MaterialTheme.typography.labelMedium,
        )
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
    onCategoryClick: () -> Unit,
    currencyCode: String = "USD",
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val isPending = log.serverVersion == 0L && log.seq == null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(start = 14.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(categoryId = log.category, onClick = onCategoryClick)
        Spacer(Modifier.width(10.dp))

        // Title + optional pending tag — fills available space
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isPending) {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Right metadata: date on top, cost below (matches iOS date + odometer layout)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = dateFormatter.format(Date(log.entryDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val displayCost = log.cost
            if (displayCost != null && displayCost > 0) {
                Text(
                    text = com.avago.core.data.Formatters.formatCurrency(displayCost, currencyCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

