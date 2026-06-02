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

            // iOS order: [reply, copy, moreReactions, (edit), (pin/unpin), (delete), report]
            // moreReactions on iOS is the smiley-plus tile inside the reactions strip above,
            // mapped on Android to the "+" square in the QuickReactions row.

            // Reply — single conditional entry mirroring iOS:
            //   subthreadRootMessageId == nil  -> "Reply in Thread" (opens subthread)
            //   else                           -> "Reply"           (inline quote in same thread)
            // Hidden entirely inside a subthread for top-level messages we don't show
            // (the subthread composer is the reply UI).
            if (!isInSubthread) {
                val isTopLevel = message.parentMessageId == null
                ActionRow(
                    label = if (isTopLevel) "Reply in Thread" else "Reply",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        if (isTopLevel) onReplyInThread() else onReply()
                        onDismiss()
                    },
                )
            }

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

            // Edit — own non-system messages only (iOS appends right after the [reply,copy,more] base)
            if (isOwn && !message.isSystem) {
                ActionRow(
                    label = "Edit",
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { onEdit(); onDismiss() },
                )
            }

            // Pin / Unpin — admin/root only
            if (isAdminOrRoot) {
                ActionRow(
                    label = if (message.isPinned) "Unpin" else "Pin",
                    icon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        if (message.isPinned) onUnpin() else onPin()
                        onDismiss()
                    },
                )
            }

            // Delete — own messages OR admin/root
            if (isOwn || isAdminOrRoot) {
                ActionRow(
                    label = "Delete",
                    icon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { onDelete(); onDismiss() },
                    labelColor = MaterialTheme.colorScheme.error,
                )
            }

            // Report — always shown, last entry
            ActionRow(
                label = "Report",
                icon = {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
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
