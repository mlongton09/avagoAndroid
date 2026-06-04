package com.avago.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Markdown insertion helpers
// ---------------------------------------------------------------------------

private fun applyInlineMarker(tfv: TextFieldValue, marker: String): TextFieldValue {
    val text = tfv.text
    val sel = tfv.selection
    return if (sel.length > 0) {
        // Wrap the selected text with the marker on both sides
        val before = text.substring(0, sel.min)
        val selected = text.substring(sel.min, sel.max)
        val after = text.substring(sel.max)
        val newText = "$before$marker$selected$marker$after"
        // Keep selection over the wrapped content (excluding markers)
        val newStart = sel.min + marker.length
        val newEnd = newStart + selected.length
        TextFieldValue(newText, TextRange(newStart, newEnd))
    } else {
        // No selection: insert marker pair and place cursor between them
        val before = text.substring(0, sel.start)
        val after = text.substring(sel.start)
        val newText = "$before$marker$marker$after"
        val cursorPos = sel.start + marker.length
        TextFieldValue(newText, TextRange(cursorPos))
    }
}

private fun applyLinePrefix(tfv: TextFieldValue, prefix: String): TextFieldValue {
    val text = tfv.text
    val cursorPos = tfv.selection.start
    // Find the start of the current line
    val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    val newCursor = cursorPos + prefix.length
    return TextFieldValue(newText, TextRange(newCursor))
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesFullScreenScreen(
    initialText: String,
    title: String = "Notes",
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(initialText)) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(tfv.text) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Formatting toolbar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = { tfv = applyInlineMarker(tfv, "**") }) {
                        Text("B", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { tfv = applyInlineMarker(tfv, "*") }) {
                        Text("I", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { tfv = applyLinePrefix(tfv, "- ") }) {
                        Text("•", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { tfv = applyLinePrefix(tfv, "# ") }) {
                        Text("#", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            HorizontalDivider()

            // Text field
            val scrollState = rememberScrollState()
            BasicTextField(
                value = tfv,
                onValueChange = { tfv = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { innerTextField ->
                    if (tfv.text.isEmpty()) {
                        Text(
                            text = "Enter notes...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}
