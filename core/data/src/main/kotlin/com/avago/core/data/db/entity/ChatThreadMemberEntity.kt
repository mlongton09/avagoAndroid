package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "thread_members",
    primaryKeys = ["thread_id", "user_id"],
    indices = [
        Index(value = ["thread_id"]),
        Index(value = ["user_id"]),
    ]
)
data class ChatThreadMemberEntity(
    @ColumnInfo(name = "thread_id")
    val threadId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "role")
    val role: String?,

    @ColumnInfo(name = "joined_at")
    val joinedAt: Long?,

    @ColumnInfo(name = "left_at")
    val leftAt: Long?,

    @ColumnInfo(name = "is_muted", defaultValue = "0")
    val isMuted: Boolean = false,
)
