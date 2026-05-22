package com.avago.feature.assets.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assets_title)) },
                actions = {
                    IconButton(onClick = onScanBarcode) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.barcode_scanner_action_description),
                        )
                    }
                },
            )
        },
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

                // Content
                if (assets.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.assets_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = assets,
                            key = { it.assetId },
                        ) { asset ->
                            AssetCard(
                                asset = asset,
                                onClick = { onAssetClick(asset.assetId) },
                            )
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
                if (asset.assetType != null) {
                    val labelResId = AssetTypes.labelResIdFor(asset.assetType!!)
                    Text(
                        text = if (labelResId != null) stringResource(labelResId)
                        else asset.assetType!!.replace("_", " ").replaceFirstChar { it.uppercase() },
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
    return try {
        if (hex != null && hex.startsWith("#") && (hex.length == 7 || hex.length == 9)) {
            val colorLong = hex.removePrefix("#").toLong(16)
            if (hex.length == 7) Color(0xFF000000L or colorLong)
            else Color(colorLong)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primaryContainer
    }
}

private fun formatDate(epochMs: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
}
