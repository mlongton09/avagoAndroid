package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scout_pending",
    indices = [Index(value = ["created_at"])]
)
data class ScoutPendingEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "transcript")
    val transcript: String,
    @ColumnInfo(name = "screen_context")
    val screenContext: String,
    @ColumnInfo(name = "skill_hint")
    val skillHint: String?,
    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
