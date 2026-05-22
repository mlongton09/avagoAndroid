package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log_cost_lines",
    indices = [
        Index(value = ["log_id"]),
        Index(value = ["account_id", "deleted_at"]),
    ]
)
data class LogCostLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "line_id")
    val lineId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "log_id")
    val logId: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "display_order")
    val displayOrder: Long,

    @ColumnInfo(name = "inventory_id")
    val inventoryId: String?,

    @ColumnInfo(name = "user_id")
    val userId: String?,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Double,

    @ColumnInfo(name = "tax_amount")
    val taxAmount: Double?,

    @ColumnInfo(name = "gl_code")
    val glCode: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "wo_id")
    val woId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
