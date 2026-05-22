package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "thread_last_read",
    primaryKeys = ["thread_id", "user_id"],
    indices = [
        Index(value = ["thread_id"]),
        Index(value = ["user_id"]),
    ]
)
data class ChatThreadLastReadEntity(
    @ColumnInfo(name = "thread_id")
    val threadId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "last_read_message_id")
    val lastReadMessageId: String?,

    @ColumnInfo(name = "last_read_at")
    val lastReadAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
