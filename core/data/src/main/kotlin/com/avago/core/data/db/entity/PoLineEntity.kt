package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "po_lines",
    indices = [
        Index(value = ["po_id"]),
        Index(value = ["part_id"]),
    ]
)
data class PoLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "po_line_id")
    val poLineId: String,

    @ColumnInfo(name = "po_id")
    val poId: String,

    @ColumnInfo(name = "part_id")
    val partId: String?,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "gl_code")
    val glCode: String?,

    @ColumnInfo(name = "received_qty")
    val receivedQty: Double?,

    @ColumnInfo(name = "display_order")
    val displayOrder: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    // Change 77: PO line linked to work order
    @ColumnInfo(name = "work_order_id")
    val workOrderId: String? = null,
)
