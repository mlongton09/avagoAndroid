package com.avago.feature.log.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.feature.log.R
import com.avago.feature.log.model.LogEditConflict
import com.avago.feature.log.model.LogRebase

/**
 * The entry changed through sync while the form was open and both sides changed the same values:
 * keep mine (applied on top of their version) or reload theirs (discards this device's edits).
 */
@Composable
fun EditConflictDialog(
    conflicts: List<LogEditConflict>,
    label: (String) -> String,
    onKeepMine: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_edit_conflict_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.log_edit_conflict_message))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.log_edit_conflict_both_changed), style = MaterialTheme.typography.titleSmall)
                val empty = stringResource(R.string.log_edit_conflict_empty_value)
                conflicts.forEach { c ->
                    Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) {}) {
                        Text(conflictLabel(c.key, label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.log_edit_conflict_mine_theirs,
                                c.mine?.takeIf { it.isNotEmpty() } ?: empty,
                                c.theirs?.takeIf { it.isNotEmpty() } ?: empty,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onKeepMine) { Text(stringResource(R.string.log_edit_conflict_keep_mine)) } },
        dismissButton = { TextButton(onClick = onReload) { Text(stringResource(R.string.log_edit_conflict_reload)) } },
    )
}

@Composable
private fun conflictLabel(key: String, label: (String) -> String): String {
    if (!key.startsWith(LogEditConflict.FIELD_PREFIX)) return label(key)
    val field = LogRebase.Field.entries.firstOrNull { LogEditConflict.FIELD_PREFIX + it.key == key } ?: return label(key)
    return stringResource(
        when (field) {
            LogRebase.Field.WORK_ORDER -> R.string.log_edit_conflict_field_work_order
            LogRebase.Field.CATEGORY -> R.string.log_edit_conflict_field_category
            LogRebase.Field.TITLE -> R.string.log_edit_conflict_field_title
            LogRebase.Field.DATE -> R.string.log_edit_conflict_field_date
            LogRebase.Field.METER -> R.string.log_edit_conflict_field_meter
            LogRebase.Field.PERFORMER -> R.string.log_edit_conflict_field_performer
            LogRebase.Field.PROVIDER -> R.string.log_edit_conflict_field_provider
            LogRebase.Field.NOTES -> R.string.log_edit_conflict_field_notes
        },
    )
}
