package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grns",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["po_id"]),
    ]
)
data class GrnEntity(
    @PrimaryKey
    @ColumnInfo(name = "grn_id")
    val grnId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "po_id")
    val poId: String?,

    @ColumnInfo(name = "grn_number")
    val grnNumber: String?,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long?,

    @ColumnInfo(name = "received_by")
    val receivedBy: String?,

    @ColumnInfo(name = "received_at_location_id")
    val receivedAtLocationId: String?,

    @ColumnInfo(name = "carrier")
    val carrier: String?,

    @ColumnInfo(name = "tracking_number")
    val trackingNumber: String?,

    @ColumnInfo(name = "packing_slip_no")
    val packingSlipNo: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "has_discrepancy")
    val hasDiscrepancy: Boolean,

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
