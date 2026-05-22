package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatMessageEntity

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "🎉", "🔥", "👀")

/**
 * Bottom sheet shown on long-press of a message bubble.
 * Offers: quick reaction row, Reply in thread, Pin/Unpin, Edit (own only), Delete (own only), Copy body.
 */
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
            // Quick reaction row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QUICK_REACTIONS.forEach { emoji ->
                    TextButton(
                        onClick = {
                            onReact(emoji)
                            onDismiss()
                        },
                    ) {
                        Text(text = emoji, fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp))
                    }
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Reply in thread
            TextButton(
                onClick = {
                    onReplyInThread()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text("Reply in thread")
            }

            // Pin / Unpin
            if (message.isPinned) {
                TextButton(
                    onClick = {
                        onUnpin()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text("Unpin message")
                }
            } else {
                TextButton(
                    onClick = {
                        onPin()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text("Pin message")
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Copy
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.bodyMd))
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text("Copy")
            }

            // Edit (own messages only)
            if (isOwn) {
                TextButton(
                    onClick = {
                        onEdit()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text("Edit")
                }

                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        "Delete",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
