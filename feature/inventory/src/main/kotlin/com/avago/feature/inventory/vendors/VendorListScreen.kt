package com.avago.feature.inventory.vendors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.ui.EmptyState
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorListScreen(
    onVendorClick: (String) -> Unit,
    onAddVendor: () -> Unit,
    viewModel: VendorListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.vendors_title)) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddVendor,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vendors_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text(stringResource(R.string.vendors_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            if (state.filtered.isEmpty() && !state.isLoading) {
                EmptyState(message = stringResource(R.string.vendors_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filtered, key = { it.vendorId }) { vendor ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVendorClick(vendor.vendorId) },
                            elevation = CardDefaults.cardElevation(1.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(vendor.name, style = MaterialTheme.typography.titleMedium)
                                vendor.email?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                vendor.phone?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
