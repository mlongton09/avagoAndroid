package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatMessageEntity

/**
 * Centered system message with a rounded pill container.
 * Background tint varies by system_kind to match iOS:
 *   closed/resolved  → secondary (green) at 0.18 alpha
 *   opened/reopened  → primary (blue)    at 0.15 alpha
 *   log/logged       → primary (blue)    at 0.15 alpha
 *   default          → surfaceVariant
 */
@Composable
fun SystemMessageBubble(message: ChatMessageEntity, modifier: Modifier = Modifier) {
    val body = message.bodyMd.trim()
    val kind = message.systemKind ?: body.lowercase()

    val icon = when {
        kind.contains("assign") -> "👤"
        kind.contains("clos") || kind.contains("resolv") -> "✅"
        kind.contains("open") || kind.contains("reopen") -> "🔧"
        kind.contains("log") -> "📋"
        else -> "ℹ️"
    }

    val bgColor = when {
        kind.contains("clos") || kind.contains("resolv") ->
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        kind.contains("open") || kind.contains("reopen") || kind.contains("log") ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = modifier
            .wrapContentWidth()
            .padding(horizontal = 28.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$icon $body",
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}
