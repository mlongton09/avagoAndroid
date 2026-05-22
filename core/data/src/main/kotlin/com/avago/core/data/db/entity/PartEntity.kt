package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parts",
    indices = [
        Index(value = ["account_id", "deleted_at"]),
        Index(value = ["sku"]),
        Index(value = ["category"]),
    ]
)
data class PartEntity(
    @PrimaryKey
    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "sku")
    val sku: String?,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "unit_of_measure")
    val unitOfMeasure: String?,

    @ColumnInfo(name = "default_vendor_id")
    val defaultVendorId: String?,

    @ColumnInfo(name = "cost")
    val cost: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "attributes")
    val attributes: String?,

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
