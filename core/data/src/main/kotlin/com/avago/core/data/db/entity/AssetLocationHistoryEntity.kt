package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "asset_location_history",
    indices = [
        Index(value = ["asset_id"]),
        Index(value = ["account_id"]),
        Index(value = ["moved_at"]),
    ]
)
data class AssetLocationHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "history_id")
    val historyId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String?,

    @ColumnInfo(name = "to_location_id")
    val toLocationId: String?,

    @ColumnInfo(name = "moved_by")
    val movedBy: String?,

    @ColumnInfo(name = "moved_at")
    val movedAt: Long,

    @ColumnInfo(name = "reason")
    val reason: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,

    // Change 131: renamed field (moved_by -> moved_by_user_id) + move reason
    @ColumnInfo(name = "moved_by_user_id")
    val movedByUserId: String? = null,

    @ColumnInfo(name = "move_reason")
    val moveReason: String? = null,

    // Change 132: GPS coordinates of the move event
    @ColumnInfo(name = "lat")
    val lat: Double? = null,

    @ColumnInfo(name = "lng")
    val lng: Double? = null,
)
