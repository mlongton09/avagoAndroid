package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["thread_id", "created_at"]),
        Index(value = ["thread_id", "parent_message_id"]),
        Index(value = ["is_pinned"]),
        Index(value = ["needs_reply"]),
    ]
)
data class ChatMessageEntity(
    @PrimaryKey @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name") val senderName: String?,
    @ColumnInfo(name = "sender_avatar_url") val senderAvatarUrl: String? = null,
    @ColumnInfo(name = "body_md") val bodyMd: String,
    @ColumnInfo(name = "body_preview") val bodyPreview: String?,
    @ColumnInfo(name = "edited_at") val editedAt: Long?,
    @ColumnInfo(name = "link_preview_title") val linkPreviewTitle: String?,
    @ColumnInfo(name = "link_preview_description") val linkPreviewDescription: String?,
    @ColumnInfo(name = "link_preview_image_url") val linkPreviewImageUrl: String?,
    @ColumnInfo(name = "link_preview_url") val linkPreviewUrl: String?,
    @ColumnInfo(name = "link_preview_site_name") val linkPreviewSiteName: String? = null,
    /** Legacy single-image field — use image_urls when available. */
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    /** JSON array of image URLs e.g. ["https://…jpg","https://…png"]. */
    @ColumnInfo(name = "image_urls") val imageUrls: String? = null,
    @ColumnInfo(name = "audio_url") val audioUrl: String? = null,
    @ColumnInfo(name = "attachment_url") val attachmentUrl: String? = null,
    @ColumnInfo(name = "attachment_name") val attachmentName: String? = null,
    @ColumnInfo(name = "attachment_size") val attachmentSize: Long? = null,
    /** JSON array of mentioned user IDs, parallel to mention_kinds. */
    @ColumnInfo(name = "mentioned_user_ids") val mentionedUserIds: String? = null,
    /** JSON array of mention kinds: "user" | "all" | "here", parallel to mentioned_user_ids. */
    @ColumnInfo(name = "mention_kinds") val mentionKinds: String? = null,
    /** True for server-generated system messages (member_added, thread_renamed, etc.). */
    @ColumnInfo(name = "is_system", defaultValue = "0") val isSystem: Boolean = false,
    @ColumnInfo(name = "system_kind") val systemKind: String? = null,
    /** Raw JSON payload specific to the system_kind. */
    @ColumnInfo(name = "system_payload") val systemPayload: String? = null,
    /** Number of replies in this message's subthread. */
    @ColumnInfo(name = "reply_count", defaultValue = "0") val replyCount: Int = 0,
    /** epoch-ms of the most recent reply, for subthread preview ordering. */
    @ColumnInfo(name = "latest_reply_at") val latestReplyAt: Long? = null,
    /** Delivery and read counters — display-only, not mutated locally. */
    @ColumnInfo(name = "delivered_by_count", defaultValue = "0") val deliveredByCount: Int = 0,
    @ColumnInfo(name = "read_by_count", defaultValue = "0") val readByCount: Int = 0,
    @ColumnInfo(name = "read_by_total", defaultValue = "0") val readByTotal: Int = 0,
    /**
     * Reaction counts as JSON map {"👍": 3, "❤️": 1}.
     * Kept separate from my_reactions so the UI can show total counts
     * without parsing the full user-keyed map.
     */
    @ColumnInfo(name = "reaction_counts") val reactionCounts: String? = null,
    /** JSON array of emoji the current user has reacted with: ["👍"]. */
    @ColumnInfo(name = "my_reactions") val myReactions: String? = null,
    /** True when this message requires an explicit acknowledgement/reply. */
    @ColumnInfo(name = "needs_reply", defaultValue = "0") val needsReply: Boolean = false,
    /** Idempotency key sent with the outbox message; echoed back by server. */
    @ColumnInfo(name = "client_ref") val clientRef: String? = null,
    @ColumnInfo(name = "outbox_status") val outboxStatus: String? = null, // null=delivered, "sending", "failed"
    @ColumnInfo(name = "server_version", defaultValue = "0") val serverVersion: Long = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "parent_message_id") val parentMessageId: String? = null,
    @ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
)
