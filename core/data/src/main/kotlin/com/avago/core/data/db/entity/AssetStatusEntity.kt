package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 13: asset status timeline history
@Entity(
    tableName = "asset_statuses",
    indices = [
        Index(value = ["asset_id"]),
        Index(value = ["account_id"]),
    ]
)
data class AssetStatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "asset_status_id")
    val assetStatusId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "downtime_type")
    val downtimeType: String?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long?,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,

    @ColumnInfo(name = "recorded_by")
    val recordedBy: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,
)
