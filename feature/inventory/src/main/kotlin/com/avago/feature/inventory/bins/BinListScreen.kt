package com.avago.feature.inventory.bins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.db.entity.BinEntity
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinListScreen(
    onBinClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BinListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.bins_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val bins = viewModel.filteredBins()

        Column(modifier = Modifier.padding(padding)) {
            // Location filter chips
            if (state.locations.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.filterLocationId == null,
                            onClick = { viewModel.setFilterLocation(null) },
                            label = { Text(stringResource(R.string.bins_filter_all)) },
                        )
                    }
                    items(state.locations, key = { it.locationId }) { loc ->
                        FilterChip(
                            selected = state.filterLocationId == loc.locationId,
                            onClick = { viewModel.setFilterLocation(loc.locationId) },
                            label = { Text(loc.name) },
                        )
                    }
                }
            }

            if (bins.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.bins_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(bins, key = { it.binId }) { bin ->
                        BinCard(
                            bin = bin,
                            locationName = viewModel.locationName(bin.locationId),
                            onClick = { onBinClick(bin.binId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BinCard(
    bin: BinEntity,
    locationName: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (bin.binType != null) {
                    SuggestionChip(onClick = {}, label = { Text(bin.binType, style = MaterialTheme.typography.labelSmall) })
                }
                if (!bin.active) {
                    Spacer(Modifier.width(6.dp))
                    SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.bins_inactive), style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(locationName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bin.code != null || bin.aisle != null) {
                val parts = listOfNotNull(
                    bin.code?.let { "Code: $it" },
                    bin.aisle?.let { "Aisle $it" },
                    bin.shelf?.let { "Shelf $it" },
                    bin.slot?.let { "Slot $it" },
                )
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Fill % bar
            if (bin.capacity != null && bin.capacity > 0 && bin.currentCount != null) {
                Spacer(Modifier.height(6.dp))
                val fillFraction = (bin.currentCount.toFloat() / bin.capacity.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fillFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${bin.currentCount} / ${bin.capacity} ${stringResource(R.string.bins_units)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
