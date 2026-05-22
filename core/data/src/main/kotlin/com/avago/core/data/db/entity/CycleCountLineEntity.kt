package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cycle_count_lines",
    indices = [
        Index(value = ["cycle_count_id"]),
        Index(value = ["part_id"]),
    ]
)
data class CycleCountLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "line_id")
    val lineId: String,

    @ColumnInfo(name = "cycle_count_id")
    val cycleCountId: String,

    @ColumnInfo(name = "inventory_id")
    val inventoryId: String,

    @ColumnInfo(name = "part_id")
    val partId: String?,

    @ColumnInfo(name = "expected_qty")
    val expectedQty: Double?,

    @ColumnInfo(name = "counted_qty")
    val countedQty: Double?,

    @ColumnInfo(name = "variance")
    val variance: Double?,

    @ColumnInfo(name = "is_counted")
    val isCounted: Boolean,

    @ColumnInfo(name = "counted_at")
    val countedAt: Long?,

    @ColumnInfo(name = "counted_by")
    val countedBy: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,
)
