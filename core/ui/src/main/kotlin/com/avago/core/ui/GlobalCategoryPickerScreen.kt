package com.avago.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * A reusable category item for [GlobalCategoryPickerScreen].
 *
 * @param key        Stable identifier (e.g. "log_cat_oil_change").
 * @param displayName Human-readable label shown in the list.
 * @param iconRes    Optional drawable resource ID for a leading icon.
 */
data class CategoryItem(
    val key: String,
    val displayName: String,
    @DrawableRes val iconRes: Int? = null,
)

/**
 * A modal bottom-sheet category picker usable from anywhere in the app.
 *
 * Displays a search field, a filterable [LazyColumn] of categories, and an
 * empty state when the search yields no results.  Selecting a row calls
 * [onSelect] with the chosen [CategoryItem] and then [onDismiss].
 *
 * Usage:
 * ```
 * var showPicker by remember { mutableStateOf(false) }
 * if (showPicker) {
 *     GlobalCategoryPickerScreen(
 *         categories = myCategories,
 *         onSelect = { item -> doSomethingWith(item) },
 *         onDismiss = { showPicker = false },
 *     )
 * }
 * ```
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
        else categories.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.global_category_picker_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.size(8.dp))

            if (filtered.isEmpty()) {
                // Empty state
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(filtered, key = { it.key }) { item ->
                        CategoryPickerRow(
                            item = item,
                            onClick = {
                                onSelect(item)
                                onDismiss()
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }

            // Bottom padding for navigation bar inset
            Spacer(modifier = Modifier.size(24.dp))
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
        item.iconRes?.let { res ->
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
