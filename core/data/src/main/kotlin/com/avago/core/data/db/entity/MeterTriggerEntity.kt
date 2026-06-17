package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 26: meter triggers for offline threshold evaluation
@Entity(
    tableName = "meter_triggers",
    indices = [Index(value = ["asset_id"])]
)
data class MeterTriggerEntity(
    @PrimaryKey
    @ColumnInfo(name = "trigger_id")
    val triggerId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "meter_type")
    val meterType: String,

    @ColumnInfo(name = "threshold_value")
    val thresholdValue: Double,

    @ColumnInfo(name = "wo_template_id")
    val woTemplateId: String?,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

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
