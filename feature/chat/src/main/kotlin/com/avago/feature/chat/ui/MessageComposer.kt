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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.network.model.LinkPreviewResponse
import java.io.File

/**
 * Pinned composer bar at the bottom of ThreadScreen.
 *
 * Features:
 *  - BasicTextField with placeholder
 *  - Send button (enabled when text is non-blank)
 *  - Edit mode: shows an "Editing" banner and cancel button
 *  - Reply mode: shows quoted message above the field ([replyingToMessage])
 *  - @ mention detection with @all/@here support
 *  - Image picker button (left of text field)
 *  - Camera capture button
 *  - Formatting toolbar toggle (bold, italic, code, link, list)
 *  - Link preview card fetched from ViewModel ([linkPreview])
 *  - Typing callback ([onTyping]) fired on each keystroke
 *  - imePadding() so the bar stays above the software keyboard
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
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(initialText)) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var showFormatting by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Gallery picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { onImageSelected?.invoke(it.toString()) }
    }

    // Camera capture
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) cameraImageUri?.let { onImageSelected?.invoke(it.toString()) }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "chat_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    // When editingMessage changes, populate field with existing body.
    val editingId = editingMessage?.messageId
    remember(editingId) {
        if (editingMessage != null) fieldValue = TextFieldValue(editingMessage.bodyMd)
    }

    fun detectMentionQuery(text: String, cursor: Int): String? {
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
            // Gallery image picker
            IconButton(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach image",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Camera capture
            IconButton(onClick = { launchCamera() }) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Formatting toggle
            IconButton(onClick = { showFormatting = !showFormatting }) {
                Icon(
                    imageVector = Icons.Default.TextFormat,
                    contentDescription = "Toggle formatting",
                    tint = if (showFormatting)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (fieldValue.text.isEmpty()) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newVal ->
                        fieldValue = newVal
                        mentionQuery = detectMentionQuery(newVal.text, newVal.selection.end)
                        onTyping?.invoke()
                        onTextChanged?.invoke(newVal.text)
                        // Notify ViewModel of URL for link preview fetching
                        val urlRegex = Regex("https?://[^\\s]+")
                        val foundUrl = urlRegex.find(newVal.text)?.value
                        onUrlDetected?.invoke(if (foundUrl != newVal.text.trim()) foundUrl else null)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 20.dp, max = 120.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            IconButton(
                onClick = {
                    val text = fieldValue.text.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        fieldValue = TextFieldValue("")
                        mentionQuery = null
                        onUrlDetected?.invoke(null)
                    }
                },
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
