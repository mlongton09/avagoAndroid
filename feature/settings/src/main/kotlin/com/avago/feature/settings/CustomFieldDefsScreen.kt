package com.avago.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.avago.core.ui.EmptyState
import kotlinx.coroutines.launch

private val ENTITY_TYPES = listOf("asset", "work_order", "part", "vendor")
private val FIELD_TYPES = listOf("text", "number", "date", "select", "checkbox")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldDefsScreen(
    onBack: () -> Unit,
    viewModel: CustomFieldDefsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }

    val fields by viewModel.fields.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Map<String, Any>?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Field") },
            text = { Text("Remove \"${deleteTarget?.get("name")}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    (deleteTarget?.get("id") as? String)?.let { viewModel.delete(it) }
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
            AddCustomFieldSheet(
                onSave = { entityType, fieldType, name, options ->
                    viewModel.create(entityType, fieldType, name, options)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showAddSheet = false }
                },
                onDismiss = { showAddSheet = false },
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Custom Fields") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Field")
            }
        },
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            fields.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(message = "No Custom Fields")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val grouped = ENTITY_TYPES.associateWith { type -> fields.filter { it["entity_type"] == type } }
                grouped.forEach { (entityType, typeFields) ->
                    if (typeFields.isNotEmpty()) {
                        item {
                            Text(
                                text = entityType.replace("_", " ").replaceFirstChar { it.uppercase() } + "s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(typeFields, key = { it["id"].toString() }) { field ->
                            CustomFieldCard(field = field, onDelete = { deleteTarget = field })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFieldCard(field: Map<String, Any>, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(field["name"] as? String ?: "", style = MaterialTheme.typography.bodyMedium)
                Text(
                    (field["field_type"] as? String ?: "text").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomFieldSheet(
    onSave: (entityType: String, fieldType: String, name: String, options: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedEntity by remember { mutableStateOf(ENTITY_TYPES[0]) }
    var selectedFieldType by remember { mutableStateOf(FIELD_TYPES[0]) }
    var optionsText by remember { mutableStateOf("") }
    var entityExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("New Custom Field", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Field Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = entityExpanded, onExpandedChange = { entityExpanded = it }) {
            OutlinedTextField(
                value = selectedEntity.replace("_", " ").replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Entity Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = entityExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = entityExpanded, onDismissRequest = { entityExpanded = false }) {
                ENTITY_TYPES.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                        onClick = { selectedEntity = type; entityExpanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
            OutlinedTextField(
                value = selectedFieldType.replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Field Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                FIELD_TYPES.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.replaceFirstChar { it.uppercase() }) },
                        onClick = { selectedFieldType = type; typeExpanded = false },
                    )
                }
            }
        }

        if (selectedFieldType == "select") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = optionsText,
                onValueChange = { optionsText = it },
                label = { Text("Options (comma-separated)") },
                placeholder = { Text("Option 1, Option 2") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val opts = if (selectedFieldType == "select") {
                            optionsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        } else emptyList()
                        onSave(selectedEntity, selectedFieldType, name.trim(), opts)
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
