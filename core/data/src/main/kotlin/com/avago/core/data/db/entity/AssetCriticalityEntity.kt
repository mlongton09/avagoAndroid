package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 31: asset criticality definitions
@Entity(
    tableName = "asset_criticalities",
    indices = [Index(value = ["account_id"])]
)
data class AssetCriticalityEntity(
    @PrimaryKey
    @ColumnInfo(name = "criticality_id")
    val criticalityId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "level")
    val level: Long,

    @ColumnInfo(name = "color")
    val color: String?,

    @ColumnInfo(name = "description")
    val description: String?,

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
