package com.avago.feature.chat.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.avago.core.data.db.entity.ChatMessageEntity
import com.avago.core.ui.MarkdownText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * A single message bubble.
 *
 * Avatar placement matches iOS: appears at the BOTTOM of a received-message group
 * ([isGroupEnd]=true), not the top. Sender name appears at the TOP ([isGroupStart]=true).
 *
 * Supports swipe-to-reply gesture (right swipe, [onReply]) and multi-image rendering.
 */
@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    myUserId: String,
    isGroupStart: Boolean,
    isGroupEnd: Boolean = true,
    modifier: Modifier = Modifier,
    onLongPress: (ChatMessageEntity) -> Unit = {},
    onOpenSubthread: ((ChatMessageEntity) -> Unit)? = null,
    onReply: ((ChatMessageEntity) -> Unit)? = null,
) {
    val isOwn = message.senderId == myUserId
    val density = LocalDensity.current
    val triggerPx = with(density) { 60.dp.toPx() }

    // Swipe-to-reply animation state
    var isDragging by remember { mutableStateOf(false) }
    var rawDrag by remember { mutableFloatStateOf(0f) }
    val dragOffset by animateFloatAsState(
        targetValue = if (isDragging) rawDrag.coerceIn(0f, 80f) else 0f,
        animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeReply",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onReply != null) Modifier.pointerInput(message.messageId) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (rawDrag >= triggerPx) onReply(message)
                            rawDrag = 0f
                        },
                        onDragCancel = { isDragging = false; rawDrag = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0) rawDrag = (rawDrag + dragAmount).coerceIn(0f, 80f)
                        },
                    )
                } else Modifier
            ),
    ) {
        // Reply arrow that appears behind the row as it slides
        if (dragOffset > 0f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(
                    alpha = (dragOffset / triggerPx).coerceIn(0f, 1f),
                ),
                modifier = Modifier
                    .align(if (isOwn) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 8.dp)
                    .size(20.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.roundToInt().coerceAtLeast(0), 0) },
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Avatar at GROUP END (last bubble of received group) — matches iOS.
            if (!isOwn) {
                if (isGroupEnd) {
                    SenderAvatar(senderId = message.senderId, name = message.senderName)
                } else {
                    Spacer(modifier = Modifier.width(34.dp))
                }
            }

            Column(
                modifier = Modifier.widthIn(max = 280.dp),
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            ) {
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

                if (message.needsReply) {
                    NeedsReplyPill(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .align(if (isOwn) Alignment.End else Alignment.Start),
                    )
                }

                BubbleBody(
                    message = message,
                    isOwn = isOwn,
                    hasLinkPreview = message.linkPreviewUrl != null,
                    onLongPress = onLongPress,
                )

                message.reactionCounts?.takeIf { it.isNotBlank() }?.let { counts ->
                    ReactionRow(
                        reactionCountsJson = counts,
                        myReactionsJson = message.myReactions,
                    )
                }

                if (message.replyCount > 0) {
                    val label = if (message.replyCount == 1) "1 reply" else "${message.replyCount} replies"
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp)
                            .then(
                                if (onOpenSubthread != null)
                                    Modifier.clickable { onOpenSubthread(message) }
                                else Modifier,
                            ),
                    )
                }

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
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun SenderAvatar(senderId: String, name: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(end = 6.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(avatarColor(senderId)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = senderInitials(name),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NeedsReplyPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "🔴 Needs acknowledgement",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
        )
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

    // Resolve images: prefer imageUrls (multi) over legacy photoUrl
    val images: List<String> = remember(message.imageUrls, message.photoUrl) {
        val urls = message.imageUrls
        if (!urls.isNullOrBlank()) {
            try {
                reactionJson.parseToJsonElement(urls).jsonArray
                    .mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                    .take(4)
            } catch (_: Exception) { emptyList() }
        } else {
            listOfNotNull(message.photoUrl?.takeIf { it.isNotBlank() })
        }
    }

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(bubbleColor)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress(message) })
            },
    ) {
        // Multi-image grid
        if (images.isNotEmpty()) {
            ImageGrid(images = images)
            if (message.bodyMd.isNotBlank()) Spacer(modifier = Modifier.height(6.dp))
        }

        val bodyText = message.bodyMd.trim()
        if (bodyText.isNotBlank()) {
            MarkdownText(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                onUrlClick = { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Timber.w(e, "MessageBubble: could not open URL %s", url)
                    }
                },
            )
        }

        if (message.editedAt != null) {
            Text(
                text = "(edited)",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.65f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

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
 * Renders up to 4 images in an iMessage-style grid:
 *   1 image  → full width
 *   2 images → side-by-side
 *   3 images → 2 on top, 1 full width below
 *   4 images → 2×2 grid
 */
@Composable
private fun ImageGrid(images: List<String>) {
    val clipped = images.take(4)
    when (clipped.size) {
        1 -> AsyncImage(
            model = clipped[0],
            contentDescription = "Image",
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.FillWidth,
        )
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            clipped.forEach { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Image",
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        3 -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                clipped.take(2).forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Image",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            AsyncImage(
                model = clipped[2],
                contentDescription = "Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
        }
        4 -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(2) { col ->
                        AsyncImage(
                            model = clipped[row * 2 + col],
                            contentDescription = "Image",
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Three-state delivery receipt for own messages.
 */
@Composable
private fun OutboxStatusIcon(message: ChatMessageEntity) {
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
        null -> when {
            message.readByCount > 0 -> Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            message.deliveredByCount > 0 -> Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            else -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * Reaction chips floating below the bubble.
 */
@Composable
private fun ReactionRow(reactionCountsJson: String, myReactionsJson: String?) {
    val mySet: Set<String> = try {
        if (myReactionsJson.isNullOrBlank()) emptySet()
        else reactionJson.parseToJsonElement(myReactionsJson).jsonArray
            .map { it.jsonPrimitive.content }.toSet()
    } catch (_: Exception) { emptySet() }

    val reactions: List<Pair<String, Int>> = try {
        reactionJson.parseToJsonElement(reactionCountsJson).jsonObject.entries.mapNotNull { (emoji, v) ->
            val count = try { v.jsonPrimitive.int } catch (_: Exception) { 0 }
            if (count > 0) emoji to count else null
        }
    } catch (_: Exception) { emptyList() }

    if (reactions.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    ) {
        reactions.forEach { (emoji, count) ->
            val isMine = emoji in mySet
            val chipBg = if (isMine)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant
            val borderMod = if (isMine)
                Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            else
                Modifier
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(chipBg)
                    .then(borderMod)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (count > 1) "$emoji $count" else emoji,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = if (isMine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

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

private val reactionJson = Json { ignoreUnknownKeys = true; isLenient = true }
