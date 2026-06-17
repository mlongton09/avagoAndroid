package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 17: work order to asset join table (multiple assets per WO)
@Entity(
    tableName = "work_order_assets",
    indices = [
        Index(value = ["wo_id"]),
        Index(value = ["asset_id"]),
    ]
)
data class WorkOrderAssetEntity(
    @PrimaryKey
    @ColumnInfo(name = "wo_asset_id")
    val woAssetId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "seq_order")
    val seqOrder: Long = 0L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,
)
