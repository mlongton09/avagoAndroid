package com.avago.feature.workorders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.avago.core.data.domain.SavedView

/**
 * Names a new saved view ([view] null) or edits one: rename, share with the account (managers
 * only), take over the current filters, or delete.
 */
@Composable
internal fun WoSavedViewDialog(
    view: SavedView?,
    canShare: Boolean,
    onSave: (name: String, shared: Boolean, withCurrentFilters: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(view?.name.orEmpty()) }
    var shared by rememberSaveable { mutableStateOf(view?.shared ?: false) }
    var withCurrentFilters by rememberSaveable { mutableStateOf(view == null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete && view != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.wo_view_delete_title, view.name)) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.wo_view_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.wo_view_cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (view == null) R.string.wo_view_save_title else R.string.wo_view_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.wo_view_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wo_view_shared), style = MaterialTheme.typography.bodyMedium)
                        if (!canShare) {
                            Text(
                                stringResource(R.string.wo_view_shared_managers),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = shared, onCheckedChange = { shared = it }, enabled = canShare || shared)
                }
                if (view != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withCurrentFilters, onCheckedChange = { withCurrentFilters = it })
                        Text(stringResource(R.string.wo_view_use_current), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.wo_view_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), shared, withCurrentFilters) }) {
                Text(stringResource(R.string.wo_view_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wo_view_cancel)) } },
    )
}
