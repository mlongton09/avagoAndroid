package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_roster",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["user_id"]),
    ]
)
data class ChatAccountRosterEntity(
    @PrimaryKey
    @ColumnInfo(name = "roster_id")
    val rosterId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "photo_url")
    val photoUrl: String?,

    @ColumnInfo(name = "role")
    val role: String?,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
