package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun FormattingToolbar(
    fieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun wrap(prefix: String, suffix: String = prefix) {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        val text = fieldValue.text
        val selected = text.substring(start, end)
        val replacement = if (selected.isNotEmpty()) "$prefix$selected$suffix" else "$prefix$suffix"
        val newText = text.substring(0, start) + replacement + text.substring(end)
        val cursorPos = if (selected.isNotEmpty()) start + replacement.length else start + prefix.length
        onValueChange(TextFieldValue(newText, TextRange(cursorPos)))
    }

    fun insertLink() {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        val text = fieldValue.text
        val selected = text.substring(start, end)
        val replacement = if (selected.isNotEmpty()) "[$selected]()" else "[]()"
        val newText = text.substring(0, start) + replacement + text.substring(end)
        // Place cursor inside the () before the closing paren
        val cursorPos = start + replacement.length - 1
        onValueChange(TextFieldValue(newText, TextRange(cursorPos)))
    }

    fun insertList() {
        val start = fieldValue.selection.min
        val end = fieldValue.selection.max
        val text = fieldValue.text
        if (start == end) {
            val isLineStart = start == 0 || text.getOrNull(start - 1) == '\n'
            val insert = if (isLineStart) "- " else "\n- "
            val newText = text.substring(0, start) + insert + text.substring(start)
            onValueChange(TextFieldValue(newText, TextRange(start + insert.length)))
        } else {
            val lines = text.substring(start, end).split("\n")
            val replacement = lines.joinToString("\n") { "- $it" }
            val newText = text.substring(0, start) + replacement + text.substring(end)
            onValueChange(TextFieldValue(newText, TextRange(start + replacement.length)))
        }
    }

    Row(modifier = modifier.padding(horizontal = 4.dp)) {
        IconButton(onClick = { wrap("**") }) {
            Icon(Icons.Default.FormatBold, contentDescription = "Bold", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { wrap("_") }) {
            Icon(Icons.Default.FormatItalic, contentDescription = "Italic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { wrap("`") }) {
            Icon(Icons.Default.Code, contentDescription = "Code", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { insertLink() }) {
            Icon(Icons.Default.Link, contentDescription = "Link", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { insertList() }) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "List", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
