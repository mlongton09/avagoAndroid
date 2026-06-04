package com.avago.feature.assets.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Searchable asset picker used when linking an asset from other features
 * (work orders, log entries, etc.). Returns the selected asset via [onAssetSelected].
 *
 * Grouped by asset type in the same order as iOS AssetGroupPickerViewController:
 * sections follow AssetTypes.all ordering, assets within each section are
 * sorted alphabetically by name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssetPickerScreen(
    onAssetSelected: (assetId: String) -> Unit,
    onBack: () -> Unit,
    /** Called when the user taps the X button to cancel the entire creation flow.
     *  Defaults to [onBack] (pop one screen) when not provided. */
    onCancel: () -> Unit = onBack,
    viewModel: AssetListViewModel = hiltViewModel(),
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Type order from AssetTypes.all — mirrors the iOS AssetTypes config JSON order.
    val orderedTypeKeys = remember { AssetTypes.all.map { it.key } }

    // Group and sort: follow AssetTypes.all order, then unknown types, alpha within.
    val grouped = remember(assets) {
        val byType = assets.groupBy { it.assetType?.lowercase()?.trim() ?: "other" }
        val result = mutableListOf<Pair<String, List<AssetEntity>>>()
        for (key in orderedTypeKeys) {
            byType[key]?.let { list ->
                result.add(key to list.sortedBy { it.name.lowercase() })
            }
        }
        // Any types not in AssetTypes.all (server-side custom types, etc.)
        for ((key, list) in byType) {
            if (key !in orderedTypeKeys) {
                result.add(key to list.sortedBy { it.name.lowercase() })
            }
        }
        result
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.asset_picker_title)) },
                navigationIcon = {
                    // X button — cancels the entire creation flow (matches iOS cancel bar button)
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.asset_picker_search_placeholder)) },
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

            if (grouped.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.asset_picker_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    grouped.forEach { (typeKey, typeAssets) ->
                        val labelResId = AssetTypes.labelResIdFor(typeKey)
                        val label = if (labelResId != null) {
                            // resolved in composable below
                            null
                        } else {
                            typeKey.replace("_", " ").uppercase()
                        }

                        stickyHeader(key = "header_$typeKey") {
                            SectionHeader(typeKey = typeKey, fallbackLabel = label)
                        }

                        items(typeAssets, key = { it.assetId }) { asset ->
                            AssetPickerRow(
                                asset = asset,
                                onClick = { onAssetSelected(asset.assetId) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(typeKey: String, fallbackLabel: String?) {
    val labelResId = AssetTypes.labelResIdFor(typeKey)
    val text = if (labelResId != null) {
        stringResource(labelResId).uppercase()
    } else {
        fallbackLabel ?: typeKey.replace("_", " ").uppercase()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit(
                1.2f,
                androidx.compose.ui.unit.TextUnitType.Sp,
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AssetPickerRow(
    asset: AssetEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssetAvatar(
            initial = asset.avatarInitial ?: asset.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
            assetType = asset.assetType,
            size = 40,
        )
        Spacer(modifier = Modifier.width(0.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(asset.make, asset.model)
                .joinToString(" · ")
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
    }
}
