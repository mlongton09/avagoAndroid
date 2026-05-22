package com.avago.feature.chat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avago.core.data.db.entity.ChatMessageEntity

/**
 * Full-width system message cell for automated WO status changes (not user messages).
 *
 * messageType "system" messages have bodyMd like "WO-1234 was assigned to Alice".
 * Rendered as a centered italic gray line with an icon prefix based on content:
 *   "assigned"           → 👤
 *   "closed"/"resolved"  → ✅
 *   "opened"/"reopened"  → 🔧
 *   "log"/"logged"       → 📋
 *   default              → ℹ️
 */
@Composable
fun SystemMessageBubble(message: ChatMessageEntity, modifier: Modifier = Modifier) {
    val body = message.bodyMd.trim()
    val icon = when {
        body.contains("assigned", ignoreCase = true) -> "👤"
        body.contains("closed", ignoreCase = true) ||
            body.contains("resolved", ignoreCase = true) -> "✅"
        body.contains("opened", ignoreCase = true) ||
            body.contains("reopened", ignoreCase = true) -> "🔧"
        body.contains("log", ignoreCase = true) ||
            body.contains("logged", ignoreCase = true) -> "📋"
        else -> "ℹ️"
    }

    Text(
        text = "$icon $body",
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
