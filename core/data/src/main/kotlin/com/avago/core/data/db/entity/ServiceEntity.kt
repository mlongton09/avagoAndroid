package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["asset_id"]),
        Index(value = ["log_id"]),
    ]
)
data class ServiceEntity(
    @PrimaryKey
    @ColumnInfo(name = "service_id")
    val serviceId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "log_id")
    val logId: String?,

    @ColumnInfo(name = "service_type")
    val serviceType: String?,

    @ColumnInfo(name = "provider_name")
    val providerName: String?,

    @ColumnInfo(name = "provider_id")
    val providerId: String?,

    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Long?,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,

    @ColumnInfo(name = "cost")
    val cost: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "status")
    val status: String?,

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
