package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_transactions")
data class InventoryTransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "inventory_id")
    val inventoryId: String,

    @ColumnInfo(name = "part_id")
    val partId: String,

    @ColumnInfo(name = "location_id")
    val locationId: String?,

    @ColumnInfo(name = "transaction_type")
    val transactionType: String,

    @ColumnInfo(name = "quantity")
    val quantity: Double,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "reference_id")
    val referenceId: String?,

    @ColumnInfo(name = "reference_type")
    val referenceType: String?,

    @ColumnInfo(name = "performed_by")
    val performedBy: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "transfer_id")
    val transferId: String?,

    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String?,

    @ColumnInfo(name = "to_location_id")
    val toLocationId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
