package com.avago.feature.assets

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avago.core.ui.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsListScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Assets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Phase 5: open AddAssetScreen */ }) {
                Icon(Icons.Default.Add, contentDescription = "Add asset")
            }
        },
    ) { padding ->
        EmptyState(
            message = "Add your first asset",
            modifier = Modifier.padding(padding),
        )
    }
}
