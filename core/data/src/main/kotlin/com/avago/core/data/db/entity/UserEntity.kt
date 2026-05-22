package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["account_id"]),
    ]
)
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String?,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "photo_url")
    val photoUrl: String?,

    @ColumnInfo(name = "role")
    val role: String?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
