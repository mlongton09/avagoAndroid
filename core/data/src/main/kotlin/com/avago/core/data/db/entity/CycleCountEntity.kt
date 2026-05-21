package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycle_counts")
data class CycleCountEntity(
    @PrimaryKey
    @ColumnInfo(name = "cycle_count_id")
    val cycleCountId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "location_id")
    val locationId: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "scope_type")
    val scopeType: String?,

    @ColumnInfo(name = "scope_value")
    val scopeValue: String?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long?,

    @ColumnInfo(name = "locked_at")
    val lockedAt: Long?,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,

    @ColumnInfo(name = "started_by")
    val startedBy: String?,

    @ColumnInfo(name = "locked_by")
    val lockedBy: String?,

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
