package com.avago.feature.assets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.data.db.entity.LogEntity
import com.avago.core.ui.EmptyState
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.assets.viewmodel.AssetDetailViewModel
import com.avago.feature.assets.viewmodel.LogsByYear
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddLogEntry: () -> Unit,
    onLogEntryClick: (entryId: String) -> Unit,
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    val asset by viewModel.asset.collectAsStateWithLifecycle()
    val logsByYear by viewModel.logsByYear.collectAsStateWithLifecycle()
    val totalCost by viewModel.totalCost.collectAsStateWithLifecycle()
    val lastServiceDate by viewModel.lastServiceDate.collectAsStateWithLifecycle()
    val latestMeterReading by viewModel.latestMeterReading.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()

    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.name ?: stringResource(R.string.asset_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.asset_detail_edit),
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.asset_detail_overflow_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.asset_detail_share_pdf)) },
                                leadingIcon = {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    // Phase 11 will implement actual PDF generation
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLogEntry) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.asset_detail_add_log),
                )
            }
        },
    ) { paddingValues ->
        if (asset == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.asset_detail_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            // Sticky header — asset identity
            item(key = "header") {
                AssetDetailHeader(asset = asset!!)
            }

            // Stats row
            item(key = "stats") {
                AssetStatsRow(
                    totalCost = totalCost,
                    lastServiceDate = lastServiceDate,
                    latestMeterReading = latestMeterReading,
                    showMeter = asset!!.meterType != null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Category filter pills
            if (availableCategories.isNotEmpty()) {
                item(key = "category_filter") {
                    CategoryFilterRow(
                        categories = availableCategories,
                        selected = categoryFilter,
                        onSelect = { viewModel.onCategoryFilterChanged(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item(key = "log_section_title") {
                Text(
                    text = stringResource(R.string.asset_detail_log_entries),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (logsByYear.isEmpty()) {
                item(key = "log_empty") {
                    EmptyState(
                        message = stringResource(R.string.asset_detail_no_logs),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                }
            } else {
                logsByYear.forEach { group ->
                    // Year sticky header
                    stickyHeader(key = "year_${group.year}") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = group.year.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }

                    items(
                        items = group.entries,
                        key = { it.entryId },
                    ) { entry ->
                        LogEntryRow(
                            entry = entry,
                            onClick = { onLogEntryClick(entry.entryId) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetDetailHeader(
    asset: AssetEntity,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AssetAvatar(
            color = asset.avatarColor,
            initial = asset.avatarInitial ?: asset.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
            assetType = asset.assetType,
            size = 64,
        )
        Column {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (asset.assetType != null) {
                val labelResId = AssetTypes.labelResIdFor(asset.assetType)
                Text(
                    text = if (labelResId != null) stringResource(labelResId)
                    else asset.assetType.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val makeModelYear = listOfNotNull(
                asset.year?.toString(),
                asset.make,
                asset.model,
            ).joinToString(" ")
            if (makeModelYear.isNotBlank()) {
                Text(
                    text = makeModelYear,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun AssetStatsRow(
    totalCost: Double,
    lastServiceDate: Long?,
    latestMeterReading: Double?,
    showMeter: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell(
                label = stringResource(R.string.asset_detail_total_cost),
                value = if (totalCost > 0.0) formatCurrency(totalCost) else stringResource(R.string.asset_detail_na),
            )
            StatCell(
                label = stringResource(R.string.asset_detail_last_service),
                value = if (lastServiceDate != null) formatDate(lastServiceDate)
                else stringResource(R.string.asset_detail_na),
            )
            if (showMeter) {
                StatCell(
                    label = stringResource(R.string.asset_detail_meter_reading),
                    value = if (latestMeterReading != null) "%.0f".format(latestMeterReading)
                    else stringResource(R.string.asset_detail_na),
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.asset_detail_filter_all)) },
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = {
                    Text(category.replace("_", " ").replaceFirstChar { it.uppercase() })
                },
            )
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (!entry.category.isNullOrBlank()) {
                Text(
                    text = entry.category.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = formatDate(entry.entryDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.cost != null && entry.cost > 0.0) {
            Text(
                text = formatCurrency(entry.cost),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))

private fun formatCurrency(amount: Double): String = try {
    NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount)
} catch (e: Exception) {
    "%.2f".format(amount)
}
