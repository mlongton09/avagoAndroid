package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mentions_of_me",
    indices = [
        Index(value = ["thread_id"]),
        Index(value = ["account_id"]),
        Index(value = ["is_read"]),
    ]
)
data class ChatMentionEntity(
    @PrimaryKey
    @ColumnInfo(name = "mention_id")
    val mentionId: String,

    @ColumnInfo(name = "thread_id")
    val threadId: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "mentioned_by")
    val mentionedBy: String?,

    @ColumnInfo(name = "is_read", defaultValue = "0")
    val isRead: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
