package com.avago.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.sync.ui.SyncConflictViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictsScreen(
    onBack: () -> Unit,
    viewModel: SyncConflictViewModel = hiltViewModel(),
) {
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sync Conflicts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (conflicts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::keepAllLocal,
                        modifier = Modifier.weight(1f),
                    ) { Text("Keep All Mine") }
                    Button(
                        onClick = viewModel::acceptAllServer,
                        modifier = Modifier.weight(1f),
                    ) { Text("Use Server For All") }
                }
                HorizontalDivider()
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (conflicts.isEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("No sync conflicts") },
                            supportingContent = { Text("Conflicts will appear here when local and server edits collide.") },
                        )
                    }
                } else {
                    items(conflicts, key = { it.queueId }) { conflict ->
                        ListItem(
                            headlineContent = { Text(conflict.displayName) },
                            supportingContent = {
                                Text("${conflict.entityType} · ${conflict.operation}\n${conflict.conflictMessage}")
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
