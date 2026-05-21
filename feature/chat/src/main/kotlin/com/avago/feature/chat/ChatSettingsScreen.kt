package com.avago.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var muted by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Conversation Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Muted") },
                    supportingContent = { Text("Silence notifications for this conversation") },
                    trailingContent = {
                        Switch(
                            checked = muted,
                            onCheckedChange = { muted = it },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Notifications") },
                    supportingContent = { Text("Push notifications are managed in system settings") },
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Leave Conversation",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Leave conversation — coming soon")
                            }
                        },
                )
            }
        }
    }
}
