package com.avago.feature.inventory.bins

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.feature.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinDetailScreen(
    binId: String,
    onBack: () -> Unit,
    viewModel: BinDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(binId) { viewModel.load(binId) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.bin?.name ?: stringResource(R.string.bins_detail_title)) },
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

        val bin = state.bin
        if (bin == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bins_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DetailRow(label = stringResource(R.string.bins_field_name), value = bin.name)
            DetailRow(label = stringResource(R.string.bins_field_location), value = state.locationName)
            if (bin.code != null) DetailRow(label = stringResource(R.string.bins_field_code), value = bin.code)
            if (bin.barcode != null) DetailRow(label = stringResource(R.string.bins_field_barcode), value = bin.barcode)
            if (bin.aisle != null) DetailRow(label = stringResource(R.string.bins_field_aisle), value = bin.aisle)
            if (bin.shelf != null) DetailRow(label = stringResource(R.string.bins_field_shelf), value = bin.shelf)
            if (bin.slot != null) DetailRow(label = stringResource(R.string.bins_field_slot), value = bin.slot)
            if (bin.binType != null) DetailRow(label = stringResource(R.string.bins_field_type), value = bin.binType)
            DetailRow(label = stringResource(R.string.bins_field_active), value = if (bin.active) stringResource(R.string.bins_yes) else stringResource(R.string.bins_no))

            if (bin.capacity != null && bin.capacity > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.bins_capacity_section), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val count = bin.currentCount ?: 0L
                val fillFraction = (count.toFloat() / bin.capacity.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fillFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$count / ${bin.capacity} ${stringResource(R.string.bins_units)} (${(fillFraction * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}
