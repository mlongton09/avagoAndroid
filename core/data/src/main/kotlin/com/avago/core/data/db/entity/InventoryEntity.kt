package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "inventory_id")
    val inventoryId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "location_id")
    val locationId: String?,

    @ColumnInfo(name = "quantity_on_hand")
    val quantityOnHand: Double,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "last_transaction_id")
    val lastTransactionId: String?,

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
