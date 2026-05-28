package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    indices = [
        Index(value = ["account_id", "next_due_at"]),
    ]
)
data class ScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "schedule_id")
    val scheduleId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,

    @ColumnInfo(name = "rrule")
    val rrule: String?,

    @ColumnInfo(name = "end_type")
    val endType: String?,

    @ColumnInfo(name = "end_count")
    val endCount: Long?,

    @ColumnInfo(name = "end_date")
    val endDate: Long?,

    @ColumnInfo(name = "meter_type")
    val meterType: String?,

    @ColumnInfo(name = "meter_due")
    val meterDue: Double?,

    @ColumnInfo(name = "meter_interval")
    val meterInterval: Double?,

    @ColumnInfo(name = "last_completed_at")
    val lastCompletedAt: Long?,

    @ColumnInfo(name = "next_due_at")
    val nextDueAt: Long?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "timezone")
    val timezone: String?,

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
