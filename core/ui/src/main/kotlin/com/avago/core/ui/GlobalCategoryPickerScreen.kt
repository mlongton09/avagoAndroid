package com.avago.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A reusable category item for [GlobalCategoryPickerScreen].
 *
 * @param key         Stable identifier (e.g. "oil_change").
 * @param displayName Human-readable label shown in the cell.
 * @param iconRes     Optional drawable resource ID for the leading icon.
 * @param color       Background tint for the icon square (matches iOS category colors).
 * @param group       Section header label (e.g. "ENGINE", "BRAKES"). Null → "Other".
 */
data class CategoryItem(
    val key: String,
    val displayName: String,
    @DrawableRes val iconRes: Int? = null,
    val color: Color? = null,
    val group: String? = null,
)

/**
 * iOS-matching modal bottom-sheet category picker.
 *
 * When [CategoryItem.group] is set on items, renders a sectioned 2-column grid with
 * uppercase section headers — identical to iOS's `CategoryPickerViewController`.
 * When no groups are set, falls back to a simple list (backwards-compatible).
 *
 * The first section is always "COMMON" (items with group == "COMMON" or group == null),
 * followed by alphabetically-sorted groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalCategoryPickerScreen(
    title: String = stringResource(R.string.global_category_picker_title),
    categories: List<CategoryItem>,
    onSelect: (CategoryItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, categories) {
        if (query.isBlank()) categories
        else categories.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.key.contains(query, ignoreCase = true)
        }
    }

    // Build ordered section map: COMMON first, then alphabetical
    val sections: List<Pair<String, List<CategoryItem>>> = remember(filtered) {
        val grouped = filtered.groupBy { it.group ?: "Other" }
        val common = grouped["COMMON"]
        val rest = (grouped - "COMMON").entries
            .sortedBy { it.key }
            .map { it.toPair() }
        buildList {
            if (!common.isNullOrEmpty()) add("COMMON" to common)
            addAll(rest)
        }
    }

    val hasGroups = categories.any { it.group != null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.global_category_picker_search)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
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
                // Sectioned 2-column grid matching iOS layout
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    sections.forEach { (sectionTitle, items) ->
                        item(key = "header_$sectionTitle") {
                            Text(
                                text = sectionTitle.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            )
                        }
                        // Pair items into rows of 2
                        val rows = items.chunked(2)
                        items(rows, key = { row -> "row_${row.first().key}" }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { item ->
                                    CategoryGridCell(
                                        item = item,
                                        onClick = { onSelect(item); onDismiss() },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // If odd item, fill with empty space
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                // Flat list (backwards-compatible)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.key }) { item ->
                        CategoryPickerRow(
                            item = item,
                            onClick = { onSelect(item); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * iOS-matching category cell: 12dp rounded card with 34dp colored icon square + bold name.
 * Matches AVCategoryCell layout: icon box (left) + name label (center).
 */
@Composable
private fun CategoryGridCell(
    item: CategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = item.color ?: MaterialTheme.colorScheme.surfaceVariant
    val iconBgColor = bgColor.copy(alpha = 1f)

    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        // iOS cell: 1pt border (surface outline)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colored icon box (34 × 34, 8dp corner radius)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                if (item.iconRes != null) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    // Fallback: first letter of display name
                    Text(
                        text = item.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Category name
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                lineHeight = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryPickerRow(
    item: CategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.color?.let { color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                item.iconRes?.let { res ->
                    Icon(
                        painter = painterResource(id = res),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        } ?: item.iconRes?.let { res ->
            Icon(
                painter = painterResource(id = res),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
