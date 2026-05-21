package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_role_permissions")
data class AccountRolePermissionsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "role_key")
    val roleKey: String,

    @ColumnInfo(name = "permissions")
    val permissions: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,
)
