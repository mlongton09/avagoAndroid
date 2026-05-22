package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presence")
data class ChatPresenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
