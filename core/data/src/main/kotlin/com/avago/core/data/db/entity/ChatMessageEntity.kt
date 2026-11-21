package com.avago.core.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageEntity(
    val messageId: String,
    val threadId: String,
    val accountId: String,
    val senderId: String,
    val senderName: String?,
    val senderAvatarUrl: String? = null,
    val bodyMd: String,
    val bodyPreview: String?,
    val editedAt: Long?,
    val linkPreviewTitle: String?,
    val linkPreviewDescription: String?,
    val linkPreviewImageUrl: String?,
    val linkPreviewUrl: String?,
    val linkPreviewSiteName: String? = null,
    /** Legacy single-image field — use image_urls when available. */
    val photoUrl: String?,
    /** JSON array of image URLs e.g. ["https://…jpg","https://…png"]. */
    val imageUrls: String? = null,
    val audioUrl: String? = null,
    val attachmentUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: Long? = null,
    /** JSON array of mentioned user IDs, parallel to mention_kinds. */
    val mentionedUserIds: String? = null,
    /** JSON array of mention kinds: "user" | "all" | "here", parallel to mentioned_user_ids. */
    val mentionKinds: String? = null,
    /** True for server-generated system messages (member_added, thread_renamed, etc.). */
    val isSystem: Boolean = false,
    val systemKind: String? = null,
    /** Raw JSON payload specific to the system_kind. */
    val systemPayload: String? = null,
    /** Number of replies in this message's subthread. */
    val replyCount: Int = 0,
    /** epoch-ms of the most recent reply, for subthread preview ordering. */
    val latestReplyAt: Long? = null,
    /** Delivery and read counters — display-only, not mutated locally. */
    val deliveredByCount: Int = 0,
    val readByCount: Int = 0,
    val readByTotal: Int = 0,
    /**
     * Reaction counts as JSON map {"👍": 3, "❤️": 1}.
     * Kept separate from my_reactions so the UI can show total counts
     * without parsing the full user-keyed map.
     */
    val reactionCounts: String? = null,
    /** JSON array of emoji the current user has reacted with: ["👍"]. */
    val myReactions: String? = null,
    /** True when this message requires an explicit acknowledgement/reply. */
    val needsReply: Boolean = false,
    /** Idempotency key sent with the outbox message; echoed back by server. */
    val clientRef: String? = null,
    val outboxStatus: String? = null, // null=delivered, "sending", "failed"
    val serverVersion: Long = 0,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val parentMessageId: String? = null,
    val isPinned: Boolean = false,
    // Change 111: document attachment indicator
    val hasAttachment: Boolean = false,
    val attachmentCount: Int = 0,
    // Change 137: voice note / audio message
    val audioKey: String? = null,
    val audioDurationS: Double? = null,
)
