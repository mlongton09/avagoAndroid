package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["thread_id"]),
        Index(value = ["account_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"]),
    ]
)
data class ChatOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_id")
    val localId: String,

    @ColumnInfo(name = "thread_id")
    val threadId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "sender_id")
    val senderId: String,

    @ColumnInfo(name = "body_md")
    val bodyMd: String?,

    @ColumnInfo(name = "photo_local_path")
    val photoLocalPath: String?,

    @ColumnInfo(name = "parent_message_id")
    val parentMessageId: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
