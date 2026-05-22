package com.avago.feature.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatAccountRosterEntity
import com.avago.core.data.db.entity.ChatMessageEntity

/**
 * Pinned composer bar at the bottom of ThreadScreen.
 *
 * Features:
 *  - BasicTextField with placeholder
 *  - Send button (enabled when text is non-blank)
 *  - Edit mode: shows an "Editing" banner and cancel button
 *  - @ mention detection with @all/@here support
 *  - Image picker button (left of text field)
 *  - Formatting toolbar toggle (bold, italic, code)
 *  - Detected URL banner shown above the composer
 *  - imePadding() so the bar stays above the software keyboard
 */
@Composable
fun MessageComposer(
    editingMessage: ChatMessageEntity?,
    members: List<ChatAccountRosterEntity>,
    onSend: (String) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onImageSelected: ((String) -> Unit)? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var showFormatting by remember { mutableStateOf(false) }
    var detectedUrl by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { onImageSelected?.invoke(it.toString()) }
    }

    // When editingMessage changes, populate the field with the existing body.
    val editingId = editingMessage?.messageId
    remember(editingId) {
        if (editingMessage != null) {
            fieldValue = TextFieldValue(editingMessage.bodyMd)
        }
    }

    // Detect @-mention query from cursor position.
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

        // Detected URL banner (conditional)
        detectedUrl?.let { url ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🔗 ${url.take(40)}${if (url.length > 40) "…" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { detectedUrl = null },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Mention autocomplete popup — shown above the composer
        mentionQuery?.let { query ->
            MentionAutocomplete(
                query = query,
                members = members,
                onSelect = { user ->
                    // Replace @query with @displayName
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
                    // Insert "@all " or "@here " replacing the @query
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Image picker button
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

            // Formatting toggle button
            IconButton(
                onClick = { showFormatting = !showFormatting },
            ) {
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
                        val urlRegex = Regex("https?://[^\\s]+")
                        val foundUrl = urlRegex.find(newVal.text)?.value
                        detectedUrl = if (foundUrl != newVal.text.trim()) foundUrl else null
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
                        detectedUrl = null
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
