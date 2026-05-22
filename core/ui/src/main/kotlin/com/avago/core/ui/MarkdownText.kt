package com.avago.core.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Renders a subset of Markdown as a Compose [Text] / [ClickableText].
 *
 * Supported syntax (delegated to [MarkdownParser]):
 *   **bold**, *italic*, `code`, ~~strikethrough~~, [text](url), newlines
 *
 * When [onUrlClick] is non-null the composable uses [ClickableText] and delivers
 * tapped URL strings to the callback. Otherwise a plain [Text] is used.
 *
 * @param text       Raw Markdown string.
 * @param modifier   Applied to the text widget.
 * @param style      Base [TextStyle]; defaults to [LocalTextStyle].
 * @param color      Text colour override; pass [Color.Unspecified] to inherit.
 * @param maxLines   Maximum number of visible lines.
 * @param onUrlClick Called with the URL string when a link is tapped.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onUrlClick: ((String) -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    val annotated = remember(text, linkColor, codeBackground) {
        MarkdownParser.toAnnotatedString(
            text = text,
            linkColor = linkColor,
            codeBackground = codeBackground,
        )
    }

    val mergedStyle = if (color != Color.Unspecified) style.copy(color = color) else style

    if (onUrlClick != null) {
        ClickableText(
            text = annotated,
            style = mergedStyle,
            maxLines = maxLines,
            modifier = modifier,
            onClick = { offset ->
                annotated
                    .getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { annotation -> onUrlClick(annotation.item) }
            },
        )
    } else {
        Text(
            text = annotated,
            style = mergedStyle,
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}
