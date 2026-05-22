package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "reactions",
    primaryKeys = ["message_id", "user_id", "emoji"],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["user_id"]),
    ]
)
data class ChatReactionEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "thread_id")
    val threadId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "emoji")
    val emoji: String,

    @ColumnInfo(name = "reacted_at")
    val reactedAt: Long,
)
