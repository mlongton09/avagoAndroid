package com.avago.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.avago.core.data.domain.FailedChange
import com.avago.core.data.domain.SyncConflict
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class FailedChangesViewModel @Inject constructor(
    private val changes: ChangeQueueRepository,
) : ViewModel() {

    val state: StateFlow<FailedChangesUiState> = combine(
        changes.observePendingCount(),
        changes.observeFailures(),
        changes.observeOpenConflicts(),
    ) { pending, failures, conflicts ->
        FailedChangesUiState(pending, failureRows(failures, conflicts))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FailedChangesUiState())

    fun dismiss(commandId: String) {
        viewModelScope.launch { changes.dismiss(commandId) }
    }

    fun dismissAll() {
        viewModelScope.launch { state.value.failures.forEach { changes.dismiss(it.change.commandId) } }
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
 * user reads why and dismisses them. Edit conflicts show the versions involved.
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
                    FailedChangeItem(row = row, onDismiss = { viewModel.dismiss(row.change.commandId) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FailedChangeItem(row: FailedChangeRow, onDismiss: () -> Unit) {
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
                    row.conflict?.let { c ->
                        Text(
                            stringResource(
                                R.string.failed_changes_conflict_versions,
                                c.baseVersion?.toString() ?: "-",
                                c.serverVersion?.toString() ?: "-",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Text(change.message ?: change.code, color = MaterialTheme.colorScheme.error)
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
