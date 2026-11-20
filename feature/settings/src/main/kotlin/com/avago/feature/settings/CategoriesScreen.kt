package com.avago.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.Category
import com.avago.core.data.domain.CategoryDraft
import com.avago.core.data.domain.CategoryKind
import com.avago.core.data.domain.WorkOrderPriority
import com.avago.core.ui.EmptyState

private const val NEW_CATEGORY = ""
private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val canEditSettings by viewModel.canEditSettings.collectAsStateWithLifecycle()
    val canManageTemplates by viewModel.canManageTemplates.collectAsStateWithLifecycle()
    val canEdit = canEditSettings || (kind == CategoryKind.WORK_ORDER && canManageTemplates)
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(resources.getString(it))
            viewModel.messageShown()
        }
    }

    deleting?.let { category ->
        DeleteDialog(category.name, onConfirm = {
            viewModel.delete(category.id)
            deleting = null
        }, onDismiss = { deleting = null })
    }

    editing?.let { id ->
        val all = categories.orEmpty()
        val stored = all.firstOrNull { it.id == id }
        if (id == NEW_CATEGORY || stored != null) {
            CategoryEditor(
                kind = kind,
                stored = stored,
                parents = all.filter { it.id != id && it.parentId != id },
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
        topBar = { AdminTopBar(R.string.admin_categories_title, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { editing = NEW_CATEGORY }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_category_new))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryKind.entries.forEach { k ->
                    FilterChip(selected = k == kind, onClick = { viewModel.selectKind(k) }, label = { Text(kindLabel(k)) })
                }
            }
            if (!canEdit) ReadOnlyBanner()
            val loaded = categories
            when {
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                loaded.isEmpty() -> EmptyState(stringResource(R.string.admin_categories_empty))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(loaded, key = { it.id }) { category ->
                        val parent = loaded.firstOrNull { it.id == category.parentId }
                        ListItem(
                            modifier = Modifier.clickable { editing = category.id },
                            leadingContent = { ColorDot(category.color) },
                            headlineContent = { Text(category.name) },
                            supportingContent = parent?.let { { Text(stringResource(R.string.admin_category_in, it.name)) } },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (category.systemCode != null) {
                                        AssistChip(onClick = { editing = category.id }, label = { Text(stringResource(R.string.admin_system)) })
                                    } else if (canEdit) {
                                        IconButton(onClick = { deleting = category }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_delete), tint = MaterialTheme.colorScheme.error)
                                        }
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

@Composable
private fun ColorDot(color: String?) {
    val parsed = color?.takeIf { HEX_COLOR.matches(it) }?.let { Color(android.graphics.Color.parseColor(it)) }
    Surface(shape = CircleShape, color = parsed ?: MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(16.dp)) {}
}

@Composable
private fun kindLabel(kind: CategoryKind): String = stringResource(
    when (kind) {
        CategoryKind.WORK_ORDER -> R.string.admin_category_kind_work_order
        CategoryKind.PRODUCT -> R.string.admin_category_kind_product
        CategoryKind.ASSET -> R.string.admin_category_kind_asset
        CategoryKind.REQUEST -> R.string.admin_category_kind_request
    },
)

@Composable
private fun priorityLabel(priority: WorkOrderPriority?): String = stringResource(
    when (priority) {
        null -> R.string.admin_none
        WorkOrderPriority.CRITICAL -> R.string.admin_priority_critical
        WorkOrderPriority.HIGH -> R.string.admin_priority_high
        WorkOrderPriority.MEDIUM -> R.string.admin_priority_medium
        WorkOrderPriority.LOW -> R.string.admin_priority_low
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditor(
    kind: CategoryKind,
    stored: Category?,
    parents: List<Category>,
    readOnly: Boolean,
    onSave: (CategoryDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(stored?.name.orEmpty()) }
    var parentId by rememberSaveable { mutableStateOf(stored?.parentId) }
    var priority by rememberSaveable { mutableStateOf(stored?.defaultPriority?.let { p -> WorkOrderPriority.entries.firstOrNull { it.wire == p } }) }
    var sla by rememberSaveable { mutableStateOf(stored?.defaultSlaHours?.toString().orEmpty()) }
    var color by rememberSaveable { mutableStateOf(stored?.color.orEmpty()) }
    val isSystem = stored?.systemCode != null
    val slaValid = sla.isBlank() || sla.trim().toIntOrNull()?.let { it > 0 } == true
    val colorValid = color.isBlank() || HEX_COLOR.matches(color.trim())
    val none = stringResource(R.string.admin_none)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(if (stored == null) R.string.admin_category_new else R.string.admin_category_edit),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true, enabled = !readOnly && !isSystem,
                label = { Text(stringResource(R.string.admin_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            ChoiceField(
                label = stringResource(R.string.admin_category_parent),
                selected = parents.firstOrNull { it.id == parentId },
                options = listOf<Category?>(null) + parents,
                text = { it?.name ?: none },
                onSelect = { parentId = it?.id },
                enabled = !readOnly,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (kind == CategoryKind.WORK_ORDER || kind == CategoryKind.REQUEST) {
                ChoiceField(
                    label = stringResource(R.string.admin_category_priority),
                    selected = priority,
                    options = listOf<WorkOrderPriority?>(null) + WorkOrderPriority.entries,
                    text = { priorityLabel(it) },
                    onSelect = { priority = it },
                    enabled = !readOnly,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = sla, onValueChange = { sla = it }, singleLine = true, enabled = !readOnly, isError = !slaValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.admin_category_sla)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            OutlinedTextField(
                value = color, onValueChange = { color = it }, singleLine = true, enabled = !readOnly, isError = !colorValid,
                leadingIcon = { ColorDot(color.trim()) },
                label = { Text(stringResource(R.string.admin_category_color)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
                if (!readOnly) {
                    TextButton(
                        enabled = name.isNotBlank() && slaValid && colorValid,
                        onClick = {
                            onSave(
                                CategoryDraft(
                                    name = name,
                                    parentId = parentId,
                                    defaultPriority = priority,
                                    defaultSlaHours = sla.trim().toIntOrNull(),
                                    color = color.trim().ifEmpty { null },
                                    icon = stored?.icon,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.admin_save)) }
                }
            }
        }
    }
}
