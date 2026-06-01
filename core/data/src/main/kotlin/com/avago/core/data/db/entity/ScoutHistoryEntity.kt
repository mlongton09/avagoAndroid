package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scout_history",
    indices = [Index(value = ["created_at"])]
)
data class ScoutHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "transcript")
    val transcript: String,
    @ColumnInfo(name = "skill_name")
    val skillName: String?,
    @ColumnInfo(name = "target_screen")
    val targetScreen: String?,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
