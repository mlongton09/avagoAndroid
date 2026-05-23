package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendor_parts",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["vendor_id"]),
        Index(value = ["part_id"]),
    ],
)
data class VendorPartEntity(
    @PrimaryKey
    @ColumnInfo(name = "vendor_part_id")
    val vendorPartId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "vendor_id")
    val vendorId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "vendor_sku")
    val vendorSku: String?,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Double?,

    @ColumnInfo(name = "moq")
    val moq: Double?,

    @ColumnInfo(name = "pack_size")
    val packSize: Double?,

    @ColumnInfo(name = "lead_days")
    val leadDays: Int?,

    @ColumnInfo(name = "is_preferred", defaultValue = "0")
    val isPreferred: Boolean = false,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

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
