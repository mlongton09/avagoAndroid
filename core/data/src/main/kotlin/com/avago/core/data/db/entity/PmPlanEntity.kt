package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 27: PM plans with METER and CALENDAR_OR_METER trigger types
@Entity(
    tableName = "pm_plans",
    indices = [
        Index(value = ["asset_id"]),
        Index(value = ["account_id"]),
    ]
)
data class PmPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "pm_plan_id")
    val pmPlanId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String?,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "trigger_type")
    val triggerType: String,

    @ColumnInfo(name = "wo_template_id")
    val woTemplateId: String?,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "next_due_at")
    val nextDueAt: Long?,

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
