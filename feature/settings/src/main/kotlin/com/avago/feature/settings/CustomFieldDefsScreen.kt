package com.avago.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldDefDraft
import com.avago.core.data.domain.CustomFieldEntity
import com.avago.core.data.domain.CustomFieldType
import com.avago.core.data.v2.commands.AdminCommands
import com.avago.core.ui.EmptyState

private const val NEW_FIELD = ""

@Composable
fun CustomFieldDefsScreen(
    onBack: () -> Unit,
    viewModel: CustomFieldDefsViewModel = hiltViewModel(),
) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<CustomFieldDef?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(resources.getString(it))
            viewModel.messageShown()
        }
    }

    deleting?.let { field ->
        DeleteDialog(field.name, onConfirm = {
            viewModel.delete(field.id)
            deleting = null
        }, onDismiss = { deleting = null })
    }

    editing?.let { id ->
        val stored = fields?.firstOrNull { it.id == id }
        if (id == NEW_FIELD || stored != null) {
            CustomFieldEditor(
                stored = stored,
                readOnly = !canEdit,
                onSave = { draft ->
                    viewModel.save(stored?.id, draft)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }

    Scaffold(
        topBar = { AdminTopBar(R.string.admin_custom_fields_title, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { editing = NEW_FIELD }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_custom_field_new))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!canEdit) ReadOnlyBanner()
            val loaded = fields
            when {
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                loaded.isEmpty() -> EmptyState(stringResource(R.string.admin_custom_fields_empty))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    loaded.groupBy { it.entity }.forEach { (entity, defs) ->
                        item(key = "entity:${entity.wire}") {
                            Text(
                                entityLabel(entity),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(defs, key = { it.id }) { def ->
                            ListItem(
                                modifier = Modifier.clickable { editing = def.id },
                                headlineContent = { Text(if (def.required) stringResource(R.string.admin_field_required_name, def.name) else def.name) },
                                supportingContent = {
                                    Text(listOfNotNull(typeLabel(def.type), def.options.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" · "))
                                },
                                trailingContent = {
                                    if (canEdit) {
                                        IconButton(onClick = { deleting = def }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun entityLabel(entity: CustomFieldEntity): String = stringResource(
    when (entity) {
        CustomFieldEntity.ASSET -> R.string.admin_entity_asset
        CustomFieldEntity.PRODUCT -> R.string.admin_entity_product
        CustomFieldEntity.WORK_ORDER -> R.string.admin_entity_work_order
    },
)

@Composable
private fun typeLabel(type: CustomFieldType): String = stringResource(
    when (type) {
        CustomFieldType.TEXT -> R.string.admin_field_type_text
        CustomFieldType.NUMBER -> R.string.admin_field_type_number
        CustomFieldType.DATE -> R.string.admin_field_type_date
        CustomFieldType.SELECT -> R.string.admin_field_type_select
        CustomFieldType.CHECKBOX -> R.string.admin_field_type_checkbox
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldEditor(
    stored: CustomFieldDef?,
    readOnly: Boolean,
    onSave: (CustomFieldDefDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(stored?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(stored?.type ?: CustomFieldType.TEXT) }
    var entity by rememberSaveable { mutableStateOf(stored?.entity ?: CustomFieldEntity.ASSET) }
    var options by rememberSaveable { mutableStateOf(stored?.options.orEmpty().joinToString("\n")) }
    var required by rememberSaveable { mutableStateOf(stored?.required ?: false) }
    var default by rememberSaveable { mutableStateOf(stored?.defaultValue.orEmpty()) }
    var sortOrder by rememberSaveable { mutableStateOf((stored?.sortOrder ?: 0).toString()) }

    val optionList = options.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val defaultValid = default.isBlank() || AdminCommands.wireValue(type, optionList, default) != null
    val sortValid = sortOrder.trim().toIntOrNull()?.let { it >= 0 } == true
    val valid = name.isNotBlank() && (type != CustomFieldType.SELECT || optionList.isNotEmpty()) && defaultValid && sortValid

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(if (stored == null) R.string.admin_custom_field_new else R.string.admin_custom_field_edit),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it }, enabled = !readOnly, singleLine = true,
                label = { Text(stringResource(R.string.admin_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            // Type and entity are fixed once the field exists.
            ChoiceField(
                label = stringResource(R.string.admin_field_entity), selected = entity, options = CustomFieldEntity.entries,
                text = { entityLabel(it) }, onSelect = { entity = it }, enabled = !readOnly && stored == null,
                modifier = Modifier.padding(top = 8.dp),
            )
            ChoiceField(
                label = stringResource(R.string.admin_field_type), selected = type, options = CustomFieldType.entries,
                text = { typeLabel(it) }, onSelect = { type = it }, enabled = !readOnly && stored == null,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (type == CustomFieldType.SELECT) {
                OutlinedTextField(
                    value = options, onValueChange = { options = it }, enabled = !readOnly, minLines = 3,
                    isError = optionList.isEmpty(),
                    label = { Text(stringResource(R.string.admin_field_options)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            OutlinedTextField(
                value = default, onValueChange = { default = it }, enabled = !readOnly, singleLine = true, isError = !defaultValid,
                label = { Text(stringResource(R.string.admin_field_default)) },
                supportingText = { Text(stringResource(defaultHint(type))) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = sortOrder, onValueChange = { sortOrder = it }, enabled = !readOnly, singleLine = true, isError = !sortValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.admin_field_sort)) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.admin_field_required), Modifier.weight(1f))
                Switch(checked = required, onCheckedChange = { required = it }, enabled = !readOnly)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
                if (!readOnly) {
                    TextButton(enabled = valid, onClick = {
                        onSave(
                            CustomFieldDefDraft(
                                name = name, type = type, entity = entity, options = optionList, required = required,
                                defaultValue = default.ifBlank { null }, sortOrder = sortOrder.trim().toInt(),
                            ),
                        )
                    }) { Text(stringResource(R.string.admin_save)) }
                }
            }
        }
    }
}

private fun defaultHint(type: CustomFieldType): Int = when (type) {
    CustomFieldType.TEXT -> R.string.admin_field_default_hint_text
    CustomFieldType.NUMBER -> R.string.admin_field_default_hint_number
    CustomFieldType.DATE -> R.string.admin_field_default_hint_date
    CustomFieldType.SELECT -> R.string.admin_field_default_hint_select
    CustomFieldType.CHECKBOX -> R.string.admin_field_default_hint_checkbox
}
