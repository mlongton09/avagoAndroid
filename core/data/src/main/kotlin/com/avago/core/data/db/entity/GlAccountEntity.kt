package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gl_accounts",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["gl_code"], unique = true),
    ]
)
data class GlAccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "gl_account_id")
    val glAccountId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "gl_code")
    val glCode: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "account_type")
    val accountType: String?,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
