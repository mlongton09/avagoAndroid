package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grn_lines",
    indices = [
        Index(value = ["grn_id"]),
        Index(value = ["part_id"]),
    ]
)
data class GrnLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "grn_line_id")
    val grnLineId: String,

    @ColumnInfo(name = "grn_id")
    val grnId: String,

    @ColumnInfo(name = "po_line_id")
    val poLineId: String?,

    @ColumnInfo(name = "part_id")
    val partId: String?,

    @ColumnInfo(name = "quantity_received")
    val quantityReceived: Double,

    @ColumnInfo(name = "quantity_expected")
    val quantityExpected: Double?,

    @ColumnInfo(name = "variance_reason")
    val varianceReason: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,
)
