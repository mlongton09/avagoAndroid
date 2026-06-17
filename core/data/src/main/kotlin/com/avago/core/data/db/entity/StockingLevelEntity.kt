package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stocking_levels",
    indices = [
        Index(value = ["part_id", "location_id"]),
    ]
)
data class StockingLevelEntity(
    @PrimaryKey
    @ColumnInfo(name = "stocking_level_id")
    val stockingLevelId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "location_id")
    val locationId: String,

    @ColumnInfo(name = "min_qty")
    val minQty: Double?,

    @ColumnInfo(name = "max_qty")
    val maxQty: Double?,

    @ColumnInfo(name = "reorder_qty")
    val reorderQty: Double?,

    @ColumnInfo(name = "safety_stock")
    val safetyStock: Double?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "safety_stock_quantity")
    val safetyStockQuantity: Double? = null,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "consumption_window")
    val consumptionWindow: Int? = null,

    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
