package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_threads",
    indices = [
        Index(value = ["account_id", "deleted_at", "last_message_at"]),
    ]
)
data class ChatThreadEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "thread_type") val threadType: String, // "team" | "direct" | "group" | "wo" | "asset"
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String?,
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long?,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "subject_summary") val subjectSummary: String?, // JSON blob
    @ColumnInfo(name = "server_version") val serverVersion: Long = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "notification_pref") val notificationPref: String? = null, // "all" | "mentions_only" | "mute"
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
