package com.avago.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * Renders a subset of Markdown as a Compose [Text].
 *
 * Supported syntax (delegated to [MarkdownParser]):
 *   **bold**, *italic*, `code`, ~~strikethrough~~, [text](url), newlines
 *
 * URL taps and long-press are handled via a single [pointerInput] using
 * [detectTapGestures] so the parent can still receive long-press in the
 * gaps between URL spans (and we don't swallow it like [androidx.compose.foundation.text.ClickableText]
 * does).
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onUrlClick: ((String) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
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
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val gestureModifier = if (onUrlClick != null || onLongPress != null) {
        Modifier.pointerInput(annotated) {
            detectTapGestures(
                onLongPress = { onLongPress?.invoke() },
                onTap = { pos ->
                    val layout = layoutResult.value ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val url = annotated
                        .getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()
                        ?.item
                    if (url != null) onUrlClick?.invoke(url)
                },
            )
        }
    } else Modifier

    Text(
        text = annotated,
        style = mergedStyle,
        maxLines = maxLines,
        modifier = modifier.then(gestureModifier),
        onTextLayout = { layoutResult.value = it },
    )
}
