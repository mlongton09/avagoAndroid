package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name") val senderName: String?,
    @ColumnInfo(name = "body_md") val bodyMd: String,
    @ColumnInfo(name = "body_preview") val bodyPreview: String?,
    @ColumnInfo(name = "edited_at") val editedAt: Long?,
    @ColumnInfo(name = "link_preview_title") val linkPreviewTitle: String?,
    @ColumnInfo(name = "link_preview_description") val linkPreviewDescription: String?,
    @ColumnInfo(name = "link_preview_image_url") val linkPreviewImageUrl: String?,
    @ColumnInfo(name = "link_preview_url") val linkPreviewUrl: String?,
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    @ColumnInfo(name = "reactions") val reactions: String?, // JSON blob: {"👍": ["userId1","userId2"]}
    @ColumnInfo(name = "outbox_status") val outboxStatus: String?, // null=delivered, "sending", "failed"
    @ColumnInfo(name = "server_version") val serverVersion: Long = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Non-null when this message is a reply to another message in a subthread. */
    @ColumnInfo(name = "parent_message_id") val parentMessageId: String? = null,
    /** True when this message has been pinned to the top of its thread. */
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
)
