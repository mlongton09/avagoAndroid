package com.avago.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.ui.MarkdownText
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
    ) {
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
                    OutboxStatusIcon(status = message.outboxStatus)
                }
            }

            // Reaction chips
            message.reactions?.takeIf { it.isNotBlank() }?.let { reactions ->
                ReactionRow(reactionsJson = reactions)
            }
        }
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
            .then(
                Modifier // Long-press handled via combinedClickable in production
            ),
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
                        timber.log.Timber.w(e, "MessageBubble: could not open URL %s", url)
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

@Composable
private fun OutboxStatusIcon(status: String?) {
    when (status) {
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
            imageVector = Icons.Default.Check,
            contentDescription = "Delivered",
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
