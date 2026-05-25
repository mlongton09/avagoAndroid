package com.avago.feature.assets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.ui.EmptyState
import com.avago.core.ui.QuoteBanner
import com.avago.core.ui.ScoutFAB
import com.avago.core.ui.ScoutViewModel
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.assets.viewmodel.AssetListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    onAssetClick: (assetId: String) -> Unit,
    onAddAsset: () -> Unit,
    onScanBarcode: () -> Unit = {},
    viewModel: AssetListViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    scoutViewModel: ScoutViewModel = hiltViewModel(),
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val showOnboarding by onboardingViewModel.showBanner.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAsset) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.assets_add_content_description),
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text(stringResource(R.string.assets_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                )

                // Filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filterType == null,
                            onClick = { viewModel.onFilterTypeChanged(null) },
                            label = { Text(stringResource(R.string.assets_filter_all)) },
                        )
                    }
                    items(AssetTypes.all) { typeItem ->
                        FilterChip(
                            selected = filterType == typeItem.key,
                            onClick = {
                                viewModel.onFilterTypeChanged(
                                    if (filterType == typeItem.key) null else typeItem.key
                                )
                            },
                            label = { Text(stringResource(typeItem.labelResId)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = typeItem.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }

                // Status filter chips (All / Active / Inactive)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = statusFilter == "all",
                            onClick = { viewModel.onStatusFilterChanged("all") },
                            label = { Text("All") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == "active",
                            onClick = { viewModel.onStatusFilterChanged("active") },
                            label = { Text("Active") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == "inactive",
                            onClick = { viewModel.onStatusFilterChanged("inactive") },
                            label = { Text("Inactive") },
                        )
                    }
                }

                // FRE banner — shown only on first launch before any assets are added
                AnimatedVisibility(
                    visible = showOnboarding,
                    enter = slideInVertically(initialOffsetY = { -it }),
                ) {
                    OnboardingBanner(
                        onAddAsset = onAddAsset,
                        onDismiss = { onboardingViewModel.dismiss() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                syncError?.let { msg ->
                    Surface(
                        onClick = { viewModel.refresh() },
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = stringResource(R.string.asset_detail_retry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                // Content
                if (assets.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.assets_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Build combined list: interleave String section headers and AssetEntity items
                    val sectionedItems: List<Any> = buildList {
                        var lastHeader: String? = null
                        assets.sortedBy { it.name.lowercase() }.forEach { asset ->
                            val header = asset.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                            if (header != lastHeader) {
                                add(header)
                                lastHeader = header
                            }
                            add(asset)
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = sectionedItems,
                            key = { item ->
                                when (item) {
                                    is String -> "header_$item"
                                    is AssetEntity -> item.assetId
                                    else -> item.hashCode()
                                }
                            },
                        ) { item ->
                            when (item) {
                                is String -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = item.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                start = 16.dp,
                                                top = 6.dp,
                                                bottom = 6.dp,
                                                end = 16.dp,
                                            ),
                                        )
                                    }
                                }
                                is AssetEntity -> {
                                    AssetCard(
                                        asset = item,
                                        onClick = { onAssetClick(item.assetId) },
                                    )
                                }
                            }
                        }

                        // Quote banner footer — only shown when there are assets and banner is dismissed
                        if (!showOnboarding) {
                            item(key = "quote_banner") {
                                QuoteBanner(
                                    modifier = Modifier.padding(vertical = 8.dp),
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
private fun AssetCard(
    asset: AssetEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color avatar with type icon
            AssetAvatar(
                color = asset.avatarColor,
                initial = asset.avatarInitial ?: asset.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                assetType = asset.assetType,
                size = 48,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (asset.make != null || asset.model != null || asset.year != null) {
                    Text(
                        text = listOfNotNull(asset.year?.toString(), asset.make, asset.model)
                            .joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                asset.assetType?.let { assetType ->
                    val labelResId = AssetTypes.labelResIdFor(assetType)
                    Text(
                        text = if (labelResId != null) stringResource(labelResId)
                        else assetType.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Last service date (updatedAt as proxy until log entries are loaded here)
            Text(
                text = formatDate(asset.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AssetAvatar(
    color: String?,
    initial: String,
    assetType: String?,
    size: Int,
    modifier: Modifier = Modifier,
) {
    val parsedColor = rememberParsedColor(color)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(parsedColor),
        contentAlignment = Alignment.Center,
    ) {
        val typeIcon = AssetTypes.iconFor(assetType)
        Icon(
            imageVector = typeIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
private fun rememberParsedColor(hex: String?): Color {
    val fallback = MaterialTheme.colorScheme.primaryContainer
    if (hex == null || !hex.startsWith("#") || (hex.length != 7 && hex.length != 9)) return fallback
    return try {
        val colorLong = hex.removePrefix("#").toLong(16)
        if (hex.length == 7) Color(0xFF000000L or colorLong) else Color(colorLong)
    } catch (e: Exception) {
        fallback
    }
}

private fun formatDate(epochMs: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
}
