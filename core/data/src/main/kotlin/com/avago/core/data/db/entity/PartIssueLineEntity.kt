package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "part_issue_lines",
    indices = [
        Index(value = ["issue_id"]),
        Index(value = ["part_id"]),
    ]
)
data class PartIssueLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "line_id")
    val lineId: String,

    @ColumnInfo(name = "issue_id")
    val issueId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "inventory_id")
    val inventoryId: String?,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Double?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,
)
