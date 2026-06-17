package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 10: asset custom status definitions
@Entity(
    tableName = "asset_custom_statuses",
    indices = [Index(value = ["account_id"])]
)
data class AssetCustomStatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "custom_status_id")
    val customStatusId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: String?,

    @ColumnInfo(name = "downtime_type")
    val downtimeType: String?,

    @ColumnInfo(name = "is_downtime", defaultValue = "0")
    val isDowntime: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,
)
