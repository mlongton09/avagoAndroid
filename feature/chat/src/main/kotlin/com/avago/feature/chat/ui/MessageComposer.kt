package com.avago.feature.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.model.LinkPreviewResponse

/**
 * Pinned composer bar at the bottom of ThreadScreen.
 *
 * iOS parity (MessageComposerView.swift):
 *  - Single "+" button on the left opens a dropdown with: Format, Attach (photos),
 *    Mention (@), Request Reply (toggle with checkmark). All secondary composer
 *    actions live in this menu; the toolbar is not a row of flat icons.
 *  - Pressing Enter sends (KeyboardOptions(imeAction = ImeAction.Send) + hardware
 *    Enter intercept). Newlines are never inserted into the buffer.
 *  - Reply quote banner, edit banner, link preview card, mention autocomplete,
 *    formatting toolbar all retained.
 *  - imePadding() keeps the bar above the software keyboard.
 *
 * Differences from iOS (intentional Android conventions):
 *  - Visible send icon on the right; Android keyboards don't consistently surface a
 *    "Send" Return-key label, and users expect a tappable button.
 */
@Composable
fun MessageComposer(
    editingMessage: ChatMessageEntity?,
    members: List<ChatAccountRosterEntity>,
    onSend: (String) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    onImageSelected: ((String) -> Unit)? = null,
    onTyping: (() -> Unit)? = null,
    onTextChanged: ((String) -> Unit)? = null,
    replyingToMessage: ChatMessageEntity? = null,
    onCancelReply: () -> Unit = {},
    linkPreview: LinkPreviewResponse? = null,
    onUrlDetected: ((String?) -> Unit)? = null,
    onDismissLinkPreview: (() -> Unit)? = null,
    needsReply: Boolean = false,
    onNeedsReplyToggle: (() -> Unit)? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(initialText)) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var showFormatting by remember { mutableStateOf(false) }
    var showPlusMenu by remember { mutableStateOf(false) }

    // Gallery picker — only attachment surface in iOS-parity composer.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { onImageSelected?.invoke(it.toString()) }
    }

    // When editingMessage changes, populate field with existing body.
    val editingId = editingMessage?.messageId
    remember(editingId) {
        if (editingMessage != null) fieldValue = TextFieldValue(editingMessage.bodyMd)
    }

    fun attemptSend() {
        val text = fieldValue.text.trim()
        if (text.isNotEmpty()) {
            onSend(text)
            fieldValue = TextFieldValue("")
            mentionQuery = null
            onUrlDetected?.invoke(null)
        }
    }

    fun insertAtSign() {
        val updated = fieldValue.insertAtCursor("@")
        fieldValue = updated
        mentionQuery = detectMentionQuery(updated.text, updated.selection.end)
    }

    Column(modifier = modifier.imePadding()) {
        // Formatting toolbar (conditional)
        if (showFormatting) {
            FormattingToolbar(
                fieldValue = fieldValue,
                onValueChange = { newVal -> fieldValue = newVal },
            )
        }

        // Link preview card (from ViewModel fetch)
        if (linkPreview != null) {
            ComposerLinkPreviewCard(
                preview = linkPreview,
                onDismiss = { onDismissLinkPreview?.invoke() },
            )
        }

        // Mention autocomplete
        mentionQuery?.let { query ->
            MentionAutocomplete(
                query = query,
                members = members,
                onSelect = { user ->
                    val text = fieldValue.text
                    val cursor = fieldValue.selection.end
                    val atIdx = text.lastIndexOf('@', cursor - 1)
                    if (atIdx >= 0) {
                        val before = text.substring(0, atIdx)
                        val after = text.substring(cursor)
                        val insert = "@${user.displayName ?: user.userId} "
                        val newText = before + insert + after
                        fieldValue = TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(before.length + insert.length),
                        )
                    }
                    mentionQuery = null
                },
                onSelectSpecial = { special ->
                    val text = fieldValue.text
                    val cursor = fieldValue.selection.end
                    val atIdx = text.lastIndexOf('@', cursor - 1)
                    if (atIdx >= 0) {
                        val before = text.substring(0, atIdx)
                        val after = text.substring(cursor)
                        val insert = "$special "
                        val newText = before + insert + after
                        fieldValue = TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(before.length + insert.length),
                        )
                    }
                    mentionQuery = null
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider()

        // Reply quote banner
        if (replyingToMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replying to ${replyingToMessage.senderName ?: "message"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = replyingToMessage.bodyMd.take(80),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onCancelReply,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel reply",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // Edit mode banner
        if (editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Editing message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onCancelEdit()
                        fieldValue = TextFieldValue("")
                        mentionQuery = null
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel edit",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "+" — opens the secondary-actions menu (Format, Attach, Mention, Request Reply)
            Box {
                IconButton(onClick = { showPlusMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "More compose actions",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(
                    expanded = showPlusMenu,
                    onDismissRequest = { showPlusMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (showFormatting) "Hide Format" else "Format") },
                        leadingIcon = {
                            Icon(Icons.Default.TextFormat, contentDescription = null)
                        },
                        trailingIcon = if (showFormatting) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            showFormatting = !showFormatting
                            showPlusMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Attach") },
                        leadingIcon = {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                        },
                        onClick = {
                            showPlusMenu = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mention") },
                        leadingIcon = {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null)
                        },
                        onClick = {
                            showPlusMenu = false
                            insertAtSign()
                        },
                    )
                    if (onNeedsReplyToggle != null) {
                        DropdownMenuItem(
                            text = { Text(if (needsReply) "Cancel Request Reply" else "Request Reply") },
                            leadingIcon = {
                                Icon(Icons.Default.PriorityHigh, contentDescription = null)
                            },
                            trailingIcon = if (needsReply) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            onClick = {
                                onNeedsReplyToggle.invoke()
                                showPlusMenu = false
                            },
                        )
                    }
                }
            }

            // Text field — pressing Enter sends (no newline insertion), matching iOS.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newVal ->
                        // Strip any embedded newlines defensively (paste/IME quirks); Enter goes through
                        // onPreviewKeyEvent/KeyboardActions.onSend, not the buffer.
                        val sanitized = if (newVal.text.contains('\n')) {
                            val cleaned = newVal.text.replace("\n", "")
                            newVal.copy(
                                text = cleaned,
                                selection = TextRange(cleaned.length.coerceAtMost(newVal.selection.end)),
                            )
                        } else newVal
                        fieldValue = sanitized
                        mentionQuery = detectMentionQuery(sanitized.text, sanitized.selection.end)
                        onTyping?.invoke()
                        onTextChanged?.invoke(sanitized.text)
                        val urlRegex = Regex("https?://[^\\s]+")
                        val foundUrl = urlRegex.find(sanitized.text)?.value
                        onUrlDetected?.invoke(if (foundUrl != sanitized.text.trim()) foundUrl else null)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { attemptSend() }),
                    // iOS MessageComposerView textView/placeholder use bodyFont
                    // (17 reg) → bodyLarge in AvagoTypography.
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        // Placeholder hint (iOS textView.attributedPlaceholder = "Message").
                        // Without this an empty composer looks like an empty pill and users
                        // can mistake it for "the composer is missing".
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = "Message",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp, max = 120.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            // Hardware/physical Enter: send, never insert newline (matches iOS).
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.Enter &&
                                !keyEvent.isShiftPressed
                            ) {
                                attemptSend()
                                true
                            } else false
                        },
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { attemptSend() },
                enabled = fieldValue.text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (fieldValue.text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

private fun TextFieldValue.insertAtCursor(insertedText: String): TextFieldValue {
    val start = selection.start.coerceAtLeast(0)
    val end = selection.end.coerceAtLeast(start)
    val updatedText = buildString {
        append(text.substring(0, start))
        append(insertedText)
        append(text.substring(end))
    }
    val newCursor = start + insertedText.length
    return copy(text = updatedText, selection = TextRange(newCursor))
}

private fun detectMentionQuery(text: String, cursor: Int): String? {
    var i = cursor - 1
    val sb = StringBuilder()
    while (i >= 0) {
        val ch = text[i]
        if (ch == '@') return sb.toString()
        if (ch == ' ' || ch == '\n') return null
        sb.insert(0, ch)
        i--
    }
    return null
}

/** Compact link preview card shown above the composer while typing a URL. */
@Composable
private fun ComposerLinkPreviewCard(
    preview: LinkPreviewResponse,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            preview.site_name?.takeIf { it.isNotBlank() }?.let { site ->
                Text(
                    text = site.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            preview.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            preview.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss preview",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
