package com.avago.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avago.core.data.domain.AccountMember
import com.avago.core.data.domain.PermissionSet
import com.avago.core.data.domain.PermissionSetDraft
import com.avago.core.data.v2.V2PermissionKey
import com.avago.core.ui.EmptyState

/** Sentinel for "creating a new set" in the editor state. */
private const val NEW_SET = ""

@Composable
fun PermissionSetsScreen(
    onBack: () -> Unit,
    viewModel: PermissionSetsViewModel = hiltViewModel(),
) {
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()
    val canAssign by viewModel.canAssign.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PermissionSet?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(resources.getString(it))
            viewModel.messageShown()
        }
    }

    deleting?.let { set ->
        DeleteDialog(set.name, onConfirm = {
            viewModel.delete(set.id)
            deleting = null
        }, onDismiss = { deleting = null })
    }

    editing?.let { id ->
        val stored = sets?.firstOrNull { it.id == id }
        if (id == NEW_SET || stored != null) {
            PermissionSetEditor(
                stored = stored,
                keysByArea = viewModel.keysByArea,
                readOnly = !canManage || stored?.isSystem == true,
                onSave = { draft ->
                    viewModel.save(stored?.id, draft)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }

    Scaffold(
        topBar = { AdminTopBar(R.string.admin_permission_sets_title, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0 && canManage) {
                FloatingActionButton(onClick = { editing = NEW_SET }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_permission_set_new))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.admin_tab_sets)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.admin_tab_members)) })
            }
            if ((tab == 0 && !canManage) || (tab == 1 && !canAssign)) ReadOnlyBanner()
            val loaded = sets
            when {
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                tab == 0 && loaded.isEmpty() -> EmptyState(stringResource(R.string.admin_permission_sets_empty))
                tab == 0 -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(loaded, key = { it.id }) { set ->
                        PermissionSetRow(
                            set = set,
                            canDelete = canManage && !set.isSystem,
                            onOpen = { editing = set.id },
                            onDelete = { deleting = set },
                        )
                        HorizontalDivider()
                    }
                }
                members.isEmpty() -> EmptyState(stringResource(R.string.admin_members_empty))
                else -> LazyColumn {
                    items(members, key = { it.userId }) { member ->
                        MemberRow(member, loaded, enabled = canAssign) { setId -> viewModel.assign(member.userId, setId) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSetRow(set: PermissionSet, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(set.name) },
        supportingContent = {
            val counts = pluralStringResource(R.plurals.admin_permissions_count, set.permissionKeys.size, set.permissionKeys.size) +
                " · " + pluralStringResource(R.plurals.admin_members_count, set.memberCount, set.memberCount)
            Text(listOfNotNull(set.description?.takeIf { it.isNotBlank() }, counts).joinToString("\n"))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (set.isSystem) AssistChip(onClick = onOpen, label = { Text(stringResource(R.string.admin_system)) })
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}

@Composable
private fun MemberRow(member: AccountMember, sets: List<PermissionSet>, enabled: Boolean, onAssign: (String?) -> Unit) {
    val none = stringResource(R.string.admin_member_no_set)
    ListItem(
        headlineContent = { Text(member.displayName ?: member.userId) },
        supportingContent = {
            Column {
                Text(roleDisplayName(member.role), style = MaterialTheme.typography.bodySmall)
                ChoiceField(
                    label = stringResource(R.string.admin_tab_sets),
                    selected = sets.firstOrNull { it.id == member.permissionSetId },
                    options = listOf<PermissionSet?>(null) + sets,
                    text = { it?.name ?: none },
                    onSelect = { onAssign(it?.id) },
                    enabled = enabled,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSetEditor(
    stored: PermissionSet?,
    keysByArea: Map<String, List<V2PermissionKey>>,
    readOnly: Boolean,
    onSave: (PermissionSetDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(stored?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(stored?.description.orEmpty()) }
    var selected by remember { mutableStateOf(stored?.permissionKeys.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) keysByArea
        else keysByArea.mapValues { (_, keys) -> keys.filter { q in it.key.lowercase() || q in it.description.lowercase() } }
            .filterValues { it.isNotEmpty() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            Text(
                stringResource(if (stored == null) R.string.admin_permission_set_new else R.string.admin_permission_set_edit),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, enabled = !readOnly, singleLine = true,
                label = { Text(stringResource(R.string.admin_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it }, enabled = !readOnly,
                label = { Text(stringResource(R.string.admin_description)) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = search, onValueChange = { search = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.admin_permission_search)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                pluralStringResource(R.plurals.admin_permissions_count, selected.size, selected.size),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                filtered.forEach { (area, keys) ->
                    item(key = "area:$area") {
                        Text(
                            area.replace('_', ' ').replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(keys, key = { it.key }) { key ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = key.key in selected,
                                enabled = !readOnly,
                                onCheckedChange = { on -> selected = if (on) selected + key.key else selected - key.key },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(key.key, style = MaterialTheme.typography.bodyMedium)
                                Text(key.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) }
                if (!readOnly) {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = { onSave(PermissionSetDraft(name, description, selected)) },
                    ) { Text(stringResource(R.string.admin_save)) }
                }
            }
        }
    }
}
