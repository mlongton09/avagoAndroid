package com.avago.feature.assets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.AssetEntity
import com.avago.core.ui.EmptyState
import com.avago.core.ui.QuoteBanner
import com.avago.feature.assets.R
import com.avago.feature.assets.model.AssetTypes
import com.avago.feature.assets.viewmodel.AssetListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssetListScreen(
    onAssetClick: (assetId: String) -> Unit,
    onAssetLongPress: (assetId: String) -> Unit = {},
    onAddAsset: () -> Unit,
    onScanBarcode: () -> Unit = {},
    viewModel: AssetListViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val presentTypes by viewModel.presentTypes.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val showOnboarding by onboardingViewModel.showBanner.collectAsStateWithLifecycle()

    var showFilterMenu by remember { mutableStateOf(false) }

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

                // Search bar + filter button in one row (matches iOS header layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )

                    // Filter button — icon when inactive, pill with × when active
                    Box {
                        if (filterType != null) {
                            val labelResId = AssetTypes.labelResIdFor(filterType)
                            val label = if (labelResId != null) stringResource(labelResId)
                            else filterType!!.replace("_", " ")
                                .replaceFirstChar { it.uppercase() }
                            Surface(
                                onClick = { viewModel.onFilterTypeChanged(null) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(
                                    text = "$label  ×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter by asset type",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assets_filter_all)) },
                                onClick = {
                                    viewModel.onFilterTypeChanged(null)
                                    showFilterMenu = false
                                },
                            )
                            presentTypes.forEach { typeKey ->
                                val labelResId = AssetTypes.labelResIdFor(typeKey)
                                val label = if (labelResId != null) stringResource(labelResId)
                                else typeKey.replace("_", " ")
                                    .replaceFirstChar { it.uppercase() }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (filterType == typeKey) "✓ $label" else label,
                                        )
                                    },
                                    onClick = {
                                        viewModel.onFilterTypeChanged(typeKey)
                                        showFilterMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                // FRE banner
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

                if (assets.isEmpty()) {
                    EmptyState(
                        message = stringResource(R.string.assets_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Build type-grouped sectioned list matching iOS section order
                    val typeOrder = AssetTypes.all.map { it.key }
                    val grouped = assets.groupBy { it.assetType ?: "other" }
                    val sectionedItems: List<Any> = buildList {
                        for (typeKey in typeOrder) {
                            val items = grouped[typeKey] ?: continue
                            add(typeKey)
                            addAll(items.sortedBy { it.name.lowercase() })
                        }
                        for ((typeKey, items) in grouped) {
                            if (typeKey !in typeOrder) {
                                add(typeKey)
                                addAll(items.sortedBy { it.name.lowercase() })
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
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
                                is String -> AssetSectionHeader(typeKey = item)
                                is AssetEntity -> {
                                    AssetRow(
                                        asset = item,
                                        onClick = { onAssetClick(item.assetId) },
                                        onLongClick = { onAssetLongPress(item.assetId) },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }

                        if (!showOnboarding) {
                            item(key = "quote_banner") {
                                QuoteBanner(modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetSectionHeader(typeKey: String) {
    val labelResId = AssetTypes.labelResIdFor(typeKey)
    val label = if (labelResId != null) stringResource(labelResId)
    else typeKey.replace("_", " ").replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetRow(
    asset: AssetEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetAvatar(
            color = asset.avatarColor,
            initial = asset.avatarInitial ?: asset.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
            assetType = asset.assetType,
            size = 40,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = listOfNotNull(asset.year?.toString(), asset.make, asset.model)
                .joinToString(" ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = formatDate(asset.updatedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        val iconRes = AssetTypes.iconResFor(assetType)
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
private fun rememberParsedColor(hex: String?): Color {
    val fallback = MaterialTheme.colorScheme.primary
    if (hex == null || !hex.startsWith("#") || (hex.length != 7 && hex.length != 9)) return fallback
    return try {
        val colorLong = hex.removePrefix("#").toLong(16)
        if (hex.length == 7) Color(0xFF000000L or colorLong) else Color(colorLong)
    } catch (_: Exception) {
        fallback
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
