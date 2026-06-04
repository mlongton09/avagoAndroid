package com.avago.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder

/**
 * A reusable category item for [GlobalCategoryPickerScreen].
 *
 * @param key           Stable identifier (e.g. "oil_change").
 * @param displayName   Human-readable label shown in the cell.
 * @param iconAssetName Optional SVG asset name from android_asset/icons without extension.
 * @param color         Background tint for the icon square (matches iOS category colors).
 * @param group         Section header label (e.g. "ENGINE", "BRAKES"). Null → "Other".
 */
data class CategoryItem(
    val key: String,
    val displayName: String,
    val iconAssetName: String? = null,
    val color: Color? = null,
    val group: String? = null,
)

/**
 * iOS-matching modal bottom-sheet category picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalCategoryPickerScreen(
    title: String = stringResource(R.string.global_category_picker_title),
    categories: List<CategoryItem>,
    recents: List<CategoryItem> = emptyList(),
    enableMultiple: Boolean = false,
    onMultipleCreate: ((selected: List<CategoryItem>, meterValue: Double?) -> Unit)? = null,
    showMeterInput: Boolean = false,
    meterUnitLabel: String? = null,
    onSelect: (CategoryItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var isMultipleMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var meterText by remember { mutableStateOf("") }

    fun CategoryItem.matchesQuery() = displayName.contains(query, ignoreCase = true) ||
        key.contains(query, ignoreCase = true)

    val filteredRecents = remember(query, recents) {
        val distinct = recents.distinctBy { it.key }
        if (query.isBlank()) distinct else distinct.filter { it.matchesQuery() }
    }

    val filtered = remember(query, categories, filteredRecents) {
        val recentKeys = if (query.isBlank()) filteredRecents.map { it.key }.toSet() else emptySet()
        val withoutRecentDuplicates = categories.filterNot { it.key in recentKeys }
        if (query.isBlank()) withoutRecentDuplicates else withoutRecentDuplicates.filter { it.matchesQuery() }
    }

    val sections: List<Pair<String, List<CategoryItem>>> = remember(filtered, filteredRecents) {
        val pinnedIds = listOf("service", "repair", "inspection", "fuel_log")
        val grouped = filtered.groupBy { it.group ?: "OTHER" }
        val commonItems = grouped["COMMON"].orEmpty()
        val commonByKey = commonItems.associateBy { it.key }
        val common = pinnedIds.mapNotNull { commonByKey[it] } + commonItems.filterNot { it.key in pinnedIds }
        val other = grouped["OTHER"].orEmpty() + grouped["Other"].orEmpty()
        val rest = grouped
            .filterKeys { it != "COMMON" && it != "OTHER" && it != "Other" }
            .entries
            .sortedBy { it.key }
            .map { it.key to it.value }
        buildList {
            if (filteredRecents.isNotEmpty()) add("RECENT" to filteredRecents)
            if (common.isNotEmpty()) add("COMMON" to common)
            addAll(rest)
            if (other.isNotEmpty()) add("OTHER" to other)
        }
    }

    val hasGroups = categories.any { it.group != null } || recents.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMultipleMode) {
                    TextButton(
                        onClick = {
                            isMultipleMode = false
                            selectedKeys = emptySet()
                            meterText = ""
                        },
                    ) { Text("Cancel") }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (enableMultiple) {
                    TextButton(
                        onClick = {
                            if (isMultipleMode) {
                                val ordered = sections.flatMap { it.second }.distinctBy { it.key }
                                val selected = ordered.filter { it.key in selectedKeys }
                                if (selected.isNotEmpty()) {
                                    onMultipleCreate?.invoke(selected, meterText.toDoubleOrNull())
                                    onDismiss()
                                }
                            } else {
                                isMultipleMode = true
                            }
                        },
                    ) { Text(if (isMultipleMode) "Create" else "Multiple") }
                }
            }

            if (isMultipleMode && showMeterInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Odometer",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = meterText,
                        onValueChange = { meterText = it },
                        suffix = { meterUnitLabel?.let { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(150.dp),
                    )
                }
            }

            // Minimal pill-style search bar — matches iOS .minimal UISearchBar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.global_category_picker_search),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty() && filteredRecents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.global_category_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (hasGroups) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    sections.forEach { (sectionTitle, items) ->
                        item(key = "header_$sectionTitle") {
                            // Sentence-case to match iOS section header style ("Recent", "Engine")
                            val displayTitle = sectionTitle.lowercase().split(" ")
                                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            )
                        }
                        items(items.chunked(2), key = { row -> "row_${row.first().key}" }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { item ->
                                    CategoryGridCell(
                                        item = item,
                                        isMultipleMode = isMultipleMode,
                                        isSelected = item.key in selectedKeys,
                                        isEnabled = !isMultipleMode || item.key != "service",
                                        onClick = {
                                            if (isMultipleMode) {
                                                if (item.key != "service") {
                                                    selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
                                                }
                                            } else {
                                                onSelect(item)
                                                onDismiss()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    items(filtered, key = { it.key }) { item ->
                        CategoryPickerRow(
                            item = item,
                            isMultipleMode = isMultipleMode,
                            isSelected = item.key in selectedKeys,
                            isEnabled = !isMultipleMode || item.key != "service",
                            onClick = {
                                if (isMultipleMode) {
                                    if (item.key != "service") {
                                        selectedKeys = if (item.key in selectedKeys) selectedKeys - item.key else selectedKeys + item.key
                                    }
                                } else {
                                    onSelect(item)
                                    onDismiss()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCell(
    item: CategoryItem,
    isMultipleMode: Boolean,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .alpha(if (isEnabled) 1f else 0.35f)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(
            item = item,
            tileSize = 34.dp,
            cornerRadius = 10.dp,
            iconSize = 18.dp,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isMultipleMode) {
            Spacer(modifier = Modifier.width(8.dp))
            CategoryCheckbox(isSelected = isSelected)
        }
    }
}

@Composable
private fun CategoryPickerRow(
    item: CategoryItem,
    isMultipleMode: Boolean,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.35f)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(
            item = item,
            tileSize = 28.dp,
            cornerRadius = 6.dp,
            iconSize = 16.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (isMultipleMode) {
            CategoryCheckbox(isSelected = isSelected)
        }
    }
}

@Composable
private fun CategoryCheckbox(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = if (isSelected) 0.dp else 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(11.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CategoryIconTile(
    item: CategoryItem,
    tileSize: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val bgColor = item.color ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(tileSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        val iconName = item.iconAssetName
        if (iconName.isNullOrBlank()) {
            CategoryLetterFallback(item.displayName)
        } else {
            CategorySvgIcon(
                iconName = iconName,
                fallbackText = item.displayName,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun CategorySvgIcon(
    iconName: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/icons/$iconName.svg")
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
        modifier = modifier,
    ) {
        when (painter.state.value) {
            is AsyncImagePainter.State.Error -> CategoryLetterFallback(fallbackText)
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
private fun CategoryLetterFallback(text: String) {
    Text(
        text = text.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

// ---------------------------------------------------------------------------
// Shared builder — used by every surface that shows a GlobalCategoryPickerScreen
// ---------------------------------------------------------------------------

/**
 * Converts a list of category key strings (e.g. "oil_change") into the
 * [CategoryItem] list that [GlobalCategoryPickerScreen] expects.
 *
 * Call this from any screen that needs a category picker — log entry,
 * work orders, etc. — so the icon, colour, group, and display-name logic
 * is always identical and lives in one place.
 *
 * @param keys       Category keys from the server config (e.g. "oil_change").
 * @param noneLabel  When non-null a "clear/none" row is prepended with key
 *                   "__none__" and group "COMMON" (used by the log entry picker).
 */
fun buildCategoryItems(
    keys: List<String>,
    noneLabel: String? = null,
): List<CategoryItem> {
    val items = keys.map { key ->
        val iconName = categoryIconName(key)
        CategoryItem(
            key = key,
            displayName = key.replace("_", " ").split(" ")
                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } },
            iconAssetName = iconName,
            color = categoryBadgeColor(iconName),
            group = categoryGroup(key),
        )
    }
    return if (noneLabel != null) {
        listOf(CategoryItem(key = "__none__", displayName = noneLabel, group = "COMMON")) + items
    } else {
        items
    }
}
