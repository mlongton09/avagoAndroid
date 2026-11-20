package com.avago.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.ConflictField
import com.avago.core.data.domain.ConflictResolution
import com.avago.core.data.domain.FailedChange
import com.avago.core.data.domain.RecordNotFoundException
import com.avago.core.data.domain.StoreNotReadyException
import com.avago.core.data.domain.SyncConflict
import com.avago.core.data.domain.fieldDiffs
import com.avago.core.data.v2.V2ApiUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A refused change with the server conflict that caused it, when there is one. */
data class FailedChangeRow(val change: FailedChange, val conflict: SyncConflict?)

data class FailedChangesUiState(
    val pendingCount: Int = 0,
    val failures: List<FailedChangeRow> = emptyList(),
    /** Open conflicts whose refused edit is not on this device (another device, or dismissed). */
    val otherConflicts: List<SyncConflict> = emptyList(),
    /** Why the last resolution could not be queued. */
    val error: ResolveError? = null,
)

enum class ResolveError {
    /** Re-applying needs the record, but it is gone locally (deleted on the server). */
    RECORD_GONE,

    /** Nobody is signed in or the local store is not open yet. */
    NOT_READY,
}

@HiltViewModel
class FailedChangesViewModel @Inject constructor(
    private val changes: ChangeQueueRepository,
) : ViewModel() {

    private val error = MutableStateFlow<ResolveError?>(null)

    val state: StateFlow<FailedChangesUiState> = combine(
        changes.observePendingCount(),
        changes.observeFailures(),
        changes.observeOpenConflicts(),
        error,
    ) { pending, failures, conflicts, error ->
        val rows = failureRows(failures, conflicts)
        val shown = rows.mapNotNullTo(mutableSetOf()) { it.conflict?.id }
        FailedChangesUiState(pending, rows, conflicts.filter { it.id !in shown }, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FailedChangesUiState())

    /**
     * Drops a refusal from the list. A refused edit with an open conflict also closes the conflict
     * on the server (`dismissed`), so it does not stay open for the user's other devices.
     */
    fun dismiss(row: FailedChangeRow) {
        val conflict = row.conflict
        if (conflict == null) {
            viewModelScope.launch { changes.dismiss(row.change.commandId) }
        } else {
            resolve(conflict, row.change.commandId, ConflictResolution.DISMISS)
        }
    }

    fun resolve(conflict: SyncConflict, failedCommandId: String?, resolution: ConflictResolution) {
        viewModelScope.launch {
            error.value = null
            try {
                changes.resolveConflict(conflict, failedCommandId, resolution)
            } catch (e: CancellationException) {
                throw e
            } catch (_: RecordNotFoundException) {
                error.value = ResolveError.RECORD_GONE
            } catch (_: StoreNotReadyException) {
                error.value = ResolveError.NOT_READY
            }
        }
    }

    fun clearError() {
        error.value = null
    }

    fun dismissAll() {
        state.value.failures.forEach(::dismiss)
    }

    companion object {
        /** Pairs each failure with its conflict (by conflict id, else by command id). */
        fun failureRows(failures: List<FailedChange>, conflicts: List<SyncConflict>): List<FailedChangeRow> {
            val byId = conflicts.associateBy { it.id }
            val byCommand = conflicts.associateBy { it.commandId }
            return failures.map { f ->
                FailedChangeRow(f, f.conflictId?.let(byId::get) ?: byCommand[f.commandId])
            }
        }

        /** `maint.work_orders.update` -> `Work orders: update`. */
        fun describeOperation(operation: String): String {
            val parts = operation.split('.')
            val verb = parts.last().replace('_', ' ')
            val subject = parts.getOrNull(parts.size - 2)?.replace('_', ' ')?.replaceFirstChar(Char::uppercaseChar)
            return if (subject == null) verb else "$subject: $verb"
        }
    }
}

/**
 * Local changes the server refused. Their optimistic effect has already been rolled back; the
 * user reads why and dismisses them. An edit conflict shows the versions and fields involved and
 * is settled with `sync.sync_conflicts.resolve`: re-apply the edit on the current record, keep the
 * server's record, or dismiss it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailedChangesScreen(
    onBack: () -> Unit,
    viewModel: FailedChangesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.failed_changes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.failed_changes_back))
                    }
                },
                actions = {
                    if (state.failures.isNotEmpty()) {
                        TextButton(onClick = viewModel::dismissAll) { Text(stringResource(R.string.failed_changes_dismiss_all)) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            if (state.pendingCount == 0) {
                                stringResource(R.string.failed_changes_all_sent)
                            } else {
                                pluralStringResource(R.plurals.failed_changes_pending, state.pendingCount, state.pendingCount)
                            },
                        )
                    },
                )
                HorizontalDivider()
            }
            if (state.failures.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.failed_changes_empty)) },
                        supportingContent = { Text(stringResource(R.string.failed_changes_empty_detail)) },
                    )
                }
            } else {
                items(state.failures, key = { it.change.commandId }) { row ->
                    FailedChangeItem(
                        row = row,
                        onDismiss = { viewModel.dismiss(row) },
                        onResolve = { conflict, resolution -> viewModel.resolve(conflict, row.change.commandId, resolution) },
                    )
                    HorizontalDivider()
                }
            }
            if (state.otherConflicts.isNotEmpty()) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.failed_changes_other_conflicts), style = MaterialTheme.typography.titleSmall)
                        },
                        supportingContent = { Text(stringResource(R.string.failed_changes_other_conflicts_detail)) },
                    )
                }
                items(state.otherConflicts, key = { it.id }) { conflict ->
                    OpenConflictItem(
                        conflict = conflict,
                        onResolve = { resolution -> viewModel.resolve(conflict, null, resolution) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.failed_changes_ok)) } },
            title = { Text(stringResource(R.string.failed_changes_resolve_failed)) },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            ResolveError.RECORD_GONE -> R.string.failed_changes_record_gone
                            ResolveError.NOT_READY -> R.string.failed_changes_not_ready
                        },
                    ),
                )
            },
        )
    }
}

@Composable
private fun FailedChangeItem(
    row: FailedChangeRow,
    onDismiss: () -> Unit,
    onResolve: (SyncConflict, ConflictResolution) -> Unit,
) {
    val change = row.change
    ListItem(
        overlineContent = {
            Text(DateUtils.getRelativeTimeSpanString(change.createdAt).toString())
        },
        headlineContent = { Text(FailedChangesViewModel.describeOperation(change.operation)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (change.isConflict) {
                    Text(stringResource(R.string.failed_changes_conflict), color = MaterialTheme.colorScheme.error)
                    val conflict = row.conflict
                    if (conflict == null) {
                        Text(stringResource(R.string.failed_changes_conflict_pending), style = MaterialTheme.typography.bodySmall)
                    } else {
                        ConflictDetails(conflict)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onResolve(conflict, ConflictResolution.KEEP_MINE) }) {
                                Text(stringResource(R.string.failed_changes_keep_mine))
                            }
                            OutlinedButton(onClick = { onResolve(conflict, ConflictResolution.KEEP_SERVER) }) {
                                Text(stringResource(R.string.failed_changes_keep_server))
                            }
                        }
                    }
                } else {
                    val reason = rejectionRes(change.code)?.let { stringResource(it) } ?: change.message ?: change.code
                    Text(reason, color = MaterialTheme.colorScheme.error)
                }
                change.entityType?.let { type ->
                    Text(
                        listOfNotNull(type, change.entityKey).joinToString(" \u00b7 "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.failed_changes_dismiss)) }
        },
    )
}

/** Localized text for upload refusals (unsupported or mismatched file type); other codes show the server message. */
private fun rejectionRes(code: String): Int? = when (code) {
    V2ApiUploader.UNSUPPORTED_MEDIA_TYPE -> R.string.failed_changes_unsupported_media_type
    V2ApiUploader.MEDIA_TYPE_MISMATCH -> R.string.failed_changes_media_type_mismatch
    else -> null
}

@Composable
private fun OpenConflictItem(conflict: SyncConflict, onResolve: (ConflictResolution) -> Unit) {
    ListItem(
        headlineContent = { Text(listOf(conflict.entityType, conflict.entityKey).joinToString(" \u00b7 ")) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ConflictDetails(conflict)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onResolve(ConflictResolution.KEEP_SERVER) }) {
                        Text(stringResource(R.string.failed_changes_keep_server))
                    }
                    TextButton(onClick = { onResolve(ConflictResolution.DISMISS) }) {
                        Text(stringResource(R.string.failed_changes_dismiss))
                    }
                }
            }
        },
    )
}

/** The versions involved and, per field, the refused value next to the server's. */
@Composable
private fun ConflictDetails(conflict: SyncConflict) {
    Text(
        stringResource(
            R.string.failed_changes_conflict_versions,
            conflict.baseVersion?.toString() ?: "-",
            conflict.serverVersion?.toString() ?: "-",
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    val empty = stringResource(R.string.failed_changes_value_empty)
    conflict.fieldDiffs().forEach { field: ConflictField ->
        Text(
            stringResource(R.string.failed_changes_field_diff, field.name, field.mine ?: empty, field.server ?: empty),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
