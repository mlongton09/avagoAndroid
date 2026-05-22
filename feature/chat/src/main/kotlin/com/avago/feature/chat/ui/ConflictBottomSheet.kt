package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.core.sync.SyncConflict

/**
 * Bottom sheet shown in MainScaffold when SyncConflictCoordinator.conflicts is non-empty.
 *
 * Each row shows the entity name + conflict message and "Keep mine" / "Use theirs" buttons.
 * The footer has "Keep all mine" and "Use server for all" bulk actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictBottomSheet(
    conflicts: List<SyncConflict>,
    onKeepMine: (SyncConflict) -> Unit,
    onUseServer: (SyncConflict) -> Unit,
    onKeepAllMine: () -> Unit,
    onUseServerAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (conflicts.isEmpty()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Sync Conflicts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Text(
                text = "${conflicts.size} item(s) could not be synced because they were changed on the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )

            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(conflicts, key = { it.queueId }) { conflict ->
                    ConflictRow(
                        conflict = conflict,
                        onKeepMine = { onKeepMine(conflict) },
                        onUseServer = { onUseServer(conflict) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            // Bulk actions footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onKeepAllMine,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Keep all mine")
                }

                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = onUseServerAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Use server for all")
                }
            }
        }
    }
}

@Composable
private fun ConflictRow(
    conflict: SyncConflict,
    onKeepMine: () -> Unit,
    onUseServer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = conflict.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (conflict.conflictMessage.isNotBlank()) {
            Text(
                text = conflict.conflictMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(
                onClick = onKeepMine,
                modifier = Modifier.weight(1f),
            ) {
                Text("Keep mine", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(
                onClick = onUseServer,
                modifier = Modifier.weight(1f),
            ) {
                Text("Use theirs", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
