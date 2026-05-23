package com.avago.feature.chat.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.ui.MarkdownText
import timber.log.Timber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * A single message bubble.
 *
 * Renders:
 *  - Sender name (received messages only, group-start)
 *  - Body text (markdown stripped to plain text)
 *  - "(edited)" label if editedAt != null
 *  - Link preview card if link preview fields are set
 *  - Reaction row
 *  - Outbox status icon (clock/exclamation for own messages)
 *  - Timestamp
 */
@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    myUserId: String,
    isGroupStart: Boolean,
    modifier: Modifier = Modifier,
    onLongPress: (ChatMessageEntity) -> Unit = {},
) {
    val isOwn = message.senderId == myUserId
    val hasLinkPreview = message.linkPreviewUrl != null

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Avatar for received messages
        if (!isOwn) {
            if (isGroupStart) {
                val bgColor = avatarColor(message.senderId)
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = senderInitials(message.senderName),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(38.dp))
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
        ) {
            // Sender name — shown only for received messages at group start.
            if (!isOwn && isGroupStart) {
                message.senderName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
            }

            // Bubble body
            BubbleBody(
                message = message,
                isOwn = isOwn,
                hasLinkPreview = hasLinkPreview,
                onLongPress = onLongPress,
            )

            // Timestamp + outbox status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
                horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            ) {
                Text(
                    text = message.createdAt.toMessageTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
                if (isOwn) {
                    Spacer(modifier = Modifier.size(4.dp))
                    OutboxStatusIcon(message = message)
                }
            }

            // Reaction chips
            message.reactions?.takeIf { it.isNotBlank() }?.let { reactions ->
                ReactionRow(reactionsJson = reactions)
            }
        }
    }
}

private fun avatarColor(senderId: String): Color {
    val colors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFF7B1FA2), Color(0xFFD32F2F), Color(0xFF00796B),
        Color(0xFF5D4037), Color(0xFF0288D1),
    )
    return colors[Math.abs(senderId.hashCode()) % colors.size]
}

private fun senderInitials(name: String?): String {
    if (name == null) return "?"
    val parts = name.trim().split(" ")
    return if (parts.size >= 2) {
        "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
    } else {
        name.take(2).uppercase()
    }
}

@Composable
private fun BubbleBody(
    message: ChatMessageEntity,
    isOwn: Boolean,
    hasLinkPreview: Boolean,
    onLongPress: (ChatMessageEntity) -> Unit,
) {
    val bubbleColor = when {
        hasLinkPreview -> Color.Transparent
        isOwn -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        hasLinkPreview -> MaterialTheme.colorScheme.onSurface
        isOwn -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(bubbleColor)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress(message) })
            },
    ) {
        // Body text — render as Markdown so bold, italic, code, links etc. display correctly.
        val bodyText = message.bodyMd.trim()

        if (bodyText.isNotBlank()) {
            MarkdownText(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                onUrlClick = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.w(e, "MessageBubble: could not open URL %s", url)
                    }
                },
            )
        }

        // "(edited)" indicator
        if (message.editedAt != null) {
            Text(
                text = "(edited)",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.65f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Link preview card
        if (message.linkPreviewUrl != null) {
            Spacer(modifier = Modifier.height(6.dp))
            LinkPreviewCard(
                message = message,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

/**
 * Three-state (actually five-state) read receipt icon for own messages:
 *   "sending"   → single clock (in flight)
 *   "failed"    → error icon
 *   null        → single gray check (sent; delivered/read unknown)
 *
 * When ChatMessageEntity gains `readAt`/`deliveredAt` fields, wire them here:
 *   readAt != null               → double filled check, primary blue tint  (DoneAll)
 *   deliveredAt != null, no read → double outlined check, gray tint        (DoneAll gray)
 *   both null                    → single check (current null branch below)
 *
 * The overload below accepts the full entity so it is ready for those fields.
 */
@Composable
private fun OutboxStatusIcon(message: ChatMessageEntity) {
    // TODO: when readAt / deliveredAt columns are added to ChatMessageEntity, replace
    //       the null branch below with:
    //         message.readAt != null      → DoneAll, primary tint
    //         message.deliveredAt != null → DoneAll, onSurface 0.4f tint
    //         else                        → Check, onSurface 0.4f tint (Sent)
    when (message.outboxStatus) {
        "sending" -> Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = "Sending",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        "failed" -> Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Failed",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        null -> Icon(
            // Single check = Sent (delivered/read state unknown until fields exist)
            imageVector = Icons.Default.Check,
            contentDescription = "Sent",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

private val reactionJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Composable
private fun ReactionRow(reactionsJson: String) {
    // Reactions JSON: {"👍": ["userId1", "userId2"]}
    val reactions: List<Pair<String, Int>> = try {
        val obj: JsonObject = reactionJson.parseToJsonElement(reactionsJson).jsonObject
        obj.entries.mapNotNull { (emoji, value) ->
            val count: Int = try {
                (value as JsonArray).size
            } catch (e: Exception) {
                try { (value as JsonObject).size } catch (e2: Exception) { 0 }
            }
            if (count > 0) emoji to count else null
        }
    } catch (e: Exception) {
        emptyList()
    }

    if (reactions.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    ) {
        reactions.forEach { (emoji, count) ->
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (count > 1) "$emoji $count" else emoji,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
