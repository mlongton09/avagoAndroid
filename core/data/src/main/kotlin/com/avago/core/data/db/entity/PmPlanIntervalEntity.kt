package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 28: PM plan multi-cycle intervals
@Entity(
    tableName = "pm_plan_intervals",
    indices = [Index(value = ["pm_plan_id"])]
)
data class PmPlanIntervalEntity(
    @PrimaryKey
    @ColumnInfo(name = "interval_id")
    val intervalId: String,

    @ColumnInfo(name = "pm_plan_id")
    val pmPlanId: String,

    @ColumnInfo(name = "cycle_number")
    val cycleNumber: Long,

    @ColumnInfo(name = "interval_value")
    val intervalValue: Double,

    @ColumnInfo(name = "interval_unit")
    val intervalUnit: String?,

    @ColumnInfo(name = "wo_template_id")
    val woTemplateId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,
)
