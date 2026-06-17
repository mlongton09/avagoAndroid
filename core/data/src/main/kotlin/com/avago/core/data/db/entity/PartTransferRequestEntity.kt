package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 146: part transfer requests
@Entity(
    tableName = "part_transfer_requests",
    indices = [
        Index(value = ["part_id"]),
        Index(value = ["account_id"]),
    ]
)
data class PartTransferRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "request_id")
    val requestId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String?,

    @ColumnInfo(name = "to_location_id")
    val toLocationId: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "requested_by")
    val requestedBy: String?,

    @ColumnInfo(name = "approved_by")
    val approvedBy: String?,

    @ColumnInfo(name = "approved_at")
    val approvedAt: Long?,

    @ColumnInfo(name = "notes")
    val notes: String?,

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
