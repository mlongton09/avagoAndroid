package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log",
    indices = [
        Index(value = ["asset_id", "log_date"]),
        Index(value = ["account_id", "deleted_at"]),
    ]
)
data class LogEntity(
    @PrimaryKey
    @ColumnInfo(name = "log_id")
    val entryId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "log_date")
    val entryDate: Long,

    @ColumnInfo(name = "meter")
    val odometerValue: Double?,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "cost")
    val cost: Double?,

    @ColumnInfo(name = "performed_by")
    val performedBy: String?,

    @ColumnInfo(name = "performed_by_user_id")
    val performedByUserId: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "data")
    val data: String?,

    @ColumnInfo(name = "attributes")
    val attributes: String?,

    @ColumnInfo(name = "cost_mode")
    val costMode: String?,

    @ColumnInfo(name = "cost_items")
    val costItems: Double?,

    @ColumnInfo(name = "cost_labor")
    val costLabor: Double?,

    @ColumnInfo(name = "cost_tax")
    val costTax: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "base_amount")
    val baseAmount: Double?,

    @ColumnInfo(name = "exchange_rate_used")
    val exchangeRateUsed: Double?,

    @ColumnInfo(name = "config_id")
    val configId: String?,

    @ColumnInfo(name = "config_version")
    val configVersion: Long?,

    @ColumnInfo(name = "service_id")
    val serviceId: String?,

    @ColumnInfo(name = "cost_misc")
    val costMisc: Double?,

    @ColumnInfo(name = "parent_id")
    val parentId: String?,

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
