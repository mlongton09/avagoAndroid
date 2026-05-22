package com.avago.feature.assets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.avago.feature.assets.R

private val DEFAULT_CATEGORIES = listOf(
    "Electrical",
    "Mechanical",
    "HVAC",
    "Plumbing",
    "IT Equipment",
    "Vehicles",
    "Other",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerScreen(
    selectedCategory: String? = null,
    onCategorySelected: (name: String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) DEFAULT_CATEGORIES
        else DEFAULT_CATEGORIES.filter { it.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.category_picker_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.category_picker_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(filtered) { category ->
                ListItem(
                    headlineContent = { Text(category) },
                    trailingContent = if (category == selectedCategory) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable(role = Role.Button) {
                        onCategorySelected(category)
                    },
                )
            }
        }
    }
}
