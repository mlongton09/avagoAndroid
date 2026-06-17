package com.avago.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avago.core.network.model.PermissionSet
import com.avago.core.ui.EmptyState
import kotlinx.coroutines.launch

private val ALL_PERMISSIONS = listOf(
    "work_orders.create", "work_orders.edit", "work_orders.delete", "work_orders.assign",
    "assets.create", "assets.edit", "assets.delete",
    "inventory.receive", "inventory.use", "reports.view",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PermissionSetsScreen(
    onBack: () -> Unit,
    viewModel: PermissionSetsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }

    val sets by viewModel.permissionSets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PermissionSet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Permission Set") },
            text = { Text("Remove \"${deleteTarget?.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.id?.let { viewModel.delete(it) }
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            AddPermissionSetSheet(
                onSave = { name, permissions ->
                    viewModel.create(name, permissions)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showAddSheet = false }
                },
                onDismiss = { showAddSheet = false },
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Permission Sets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Permission Set")
            }
        },
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            sets.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Add,
                    title = "No Permission Sets",
                    subtitle = "Create a set to define role-based access.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sets, key = { it.id }) { set ->
                    PermissionSetCard(
                        set = set,
                        onDelete = { deleteTarget = set },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionSetCard(
    set: PermissionSet,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(set.name, style = MaterialTheme.typography.titleSmall)
                    set.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (set.permissions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    set.permissions.forEach { perm ->
                        FilterChip(selected = true, onClick = {}, label = { Text(perm, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPermissionSetSheet(
    onSave: (name: String, permissions: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("New Permission Set", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Text("Permissions", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        ALL_PERMISSIONS.forEach { perm ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = perm in selected,
                    onCheckedChange = { checked ->
                        selected = if (checked) selected + perm else selected - perm
                    },
                )
                Text(perm, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), selected.toList()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
