package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "role_permission_defaults")
data class RolePermissionDefaultsEntity(
    @PrimaryKey
    @ColumnInfo(name = "role_key")
    val roleKey: String,

    @ColumnInfo(name = "permissions")
    val permissions: String,
)
