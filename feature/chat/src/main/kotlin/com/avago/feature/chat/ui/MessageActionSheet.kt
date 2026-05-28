package com.avago.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatMessageEntity

// Quick reactions match iOS exactly: ❤️ 👍 👎 😂 ‼️ ❓
private val QUICK_REACTIONS = listOf("❤️", "👍", "👎", "😂", "‼️", "❓")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: ChatMessageEntity,
    myUserId: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    /** Inline reply — shows quoted message above composer (main thread only). */
    onReply: () -> Unit = {},
    /** Opens / continues the subthread for this message. */
    onReplyInThread: () -> Unit = {},
    onPin: () -> Unit = {},
    onUnpin: () -> Unit = {},
    onReport: () -> Unit = {},
    /**
     * True when current user is admin or root — controls pin/unpin visibility and
     * whether they can delete other users' messages (matching iOS role check).
     */
    isAdminOrRoot: Boolean = false,
    isInSubthread: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val isOwn = message.senderId == myUserId
    var showEmojiPicker by remember { mutableStateOf(false) }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            onEmojiSelected = { emoji ->
                onReact(emoji)
                onDismiss()
            },
            onDismiss = { showEmojiPicker = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // Message preview header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(12.dp),
            ) {
                message.senderName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = message.bodyPreview ?: message.bodyMd,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quick reactions (matching iOS: ❤️ 👍 👎 😂 ‼️ ❓) + full picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    TextButton(
                        onClick = {
                            onReact(emoji)
                            onDismiss()
                        },
                    ) {
                        Text(text = emoji, fontSize = TextUnit(22f, TextUnitType.Sp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "More reactions",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // Copy
            ActionRow(
                label = "Copy",
                icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("message", message.bodyMd))
                    onDismiss()
                },
            )

            // Reply — label is "Reply" in main thread, not shown in subthread
            // (subthread replies use the composer directly)
            if (!isInSubthread) {
                ActionRow(
                    label = "Reply",
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { onReply(); onDismiss() },
                )

                ActionRow(
                    label = "Reply in Thread",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    onClick = { onReplyInThread(); onDismiss() },
                )
            }

            // Pin / Unpin — admin/root only (matching iOS)
            if (isAdminOrRoot) {
                if (message.isPinned) {
                    ActionRow(
                        label = "Unpin",
                        icon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { onUnpin(); onDismiss() },
                    )
                } else {
                    ActionRow(
                        label = "Pin",
                        icon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { onPin(); onDismiss() },
                    )
                }
            }

            // Edit — own non-system messages only
            if (isOwn && !message.isSystem) {
                HorizontalDivider()
                ActionRow(
                    label = "Edit",
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { onEdit(); onDismiss() },
                )
            }

            // Delete — own messages OR admin (matching iOS)
            if (isOwn || isAdminOrRoot) {
                if (!isOwn) HorizontalDivider() // separator only when admin deleting others' messages
                ActionRow(
                    label = "Delete",
                    icon = {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { onDelete(); onDismiss() },
                    labelColor = MaterialTheme.colorScheme.error,
                )
            }

            // Report (always shown, matching iOS)
            HorizontalDivider()
            ActionRow(
                label = "Report",
                icon = {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                },
                onClick = { onReport(); onDismiss() },
                labelColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    labelColor: Color = Color.Unspecified,
) {
    ListItem(
        headlineContent = { Text(text = label, color = labelColor) },
        leadingContent = icon,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 0.dp,
    )
}
