package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 12: meter readings with triggered WO IDs
@Entity(
    tableName = "meter_readings",
    indices = [
        Index(value = ["asset_id"]),
        Index(value = ["account_id"]),
    ]
)
data class MeterReadingEntity(
    @PrimaryKey
    @ColumnInfo(name = "meter_reading_id")
    val meterReadingId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "meter_type")
    val meterType: String,

    @ColumnInfo(name = "reading_value")
    val readingValue: Double,

    @ColumnInfo(name = "read_at")
    val readAt: Long,

    @ColumnInfo(name = "recorded_by")
    val recordedBy: String?,

    @ColumnInfo(name = "triggered_wo_ids")
    val triggeredWoIds: String?,

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
