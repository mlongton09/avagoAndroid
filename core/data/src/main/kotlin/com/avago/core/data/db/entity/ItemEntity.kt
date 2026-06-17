package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["log_id"]),
        Index(value = ["account_id"]),
    ]
)
data class ItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "log_id")
    val logId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "part_id")
    val partId: String?,

    @ColumnInfo(name = "name")
    val description: String?,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit_price")
    val unitCost: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "production_date")
    val productionDate: Long?,

    @ColumnInfo(name = "part_number")
    val partNumber: String?,

    @ColumnInfo(name = "gtin")
    val gtin: String?,

    @ColumnInfo(name = "manufacturer_id")
    val manufacturerId: String?,

    @ColumnInfo(name = "serial_number")
    val serialNumber: String?,

    @ColumnInfo(name = "revision")
    val revision: String?,

    @ColumnInfo(name = "model_number")
    val modelNumber: String?,

    @ColumnInfo(name = "lot_number")
    val lotNumber: String?,

    @ColumnInfo(name = "country")
    val country: String?,

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

    // Change 86: auto-deduct inventory flag and linked transaction
    @ColumnInfo(name = "deduct_inventory", defaultValue = "0")
    val deductInventory: Boolean = false,

    @ColumnInfo(name = "inventory_transaction_id")
    val inventoryTransactionId: String? = null,
)
