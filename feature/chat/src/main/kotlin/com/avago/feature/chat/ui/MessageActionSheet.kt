package com.avago.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatMessageEntity

private val QUICK_REACTIONS = listOf("✅", "👍", "🙏", "🔧", "🤣")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: ChatMessageEntity,
    myUserId: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onReplyInThread: () -> Unit = {},
    onPin: () -> Unit = {},
    onUnpin: () -> Unit = {},
    isInSubthread: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    val isOwn = message.senderId == myUserId

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

            // Quick reaction row
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
            }

            HorizontalDivider()

            // Copy (always)
            ActionRow(
                label = "Copy",
                icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.bodyMd))
                    onDismiss()
                },
            )

            // Reply in Thread (only when not already in a subthread)
            if (!isInSubthread) {
                ActionRow(
                    label = "Reply in Thread",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        onReplyInThread()
                        onDismiss()
                    },
                )
            }

            // Pin / Unpin
            if (message.isPinned) {
                ActionRow(
                    label = "Unpin",
                    icon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onUnpin()
                        onDismiss()
                    },
                )
            } else {
                ActionRow(
                    label = "Pin",
                    icon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onPin()
                        onDismiss()
                    },
                )
            }

            // Edit / Delete (own messages only)
            if (isOwn) {
                HorizontalDivider()

                ActionRow(
                    label = "Edit",
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        onEdit()
                        onDismiss()
                    },
                )

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
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    labelColor = MaterialTheme.colorScheme.error,
                )
            }
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
