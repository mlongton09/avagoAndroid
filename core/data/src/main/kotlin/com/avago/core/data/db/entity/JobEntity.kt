package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jobs",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["asset_id"]),
        Index(value = ["status"]),
    ]
)
data class JobEntity(
    @PrimaryKey
    @ColumnInfo(name = "job_id")
    val jobId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String?,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "job_type")
    val jobType: String?,

    @ColumnInfo(name = "priority")
    val priority: String?,

    @ColumnInfo(name = "assigned_to")
    val assignedTo: String?,

    @ColumnInfo(name = "due_date")
    val dueDate: Long?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long?,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "attributes")
    val attributes: String?,

    @ColumnInfo(name = "created_by")
    val createdBy: String?,

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

    // Change 127: budget alert threshold (% of budget)
    @ColumnInfo(name = "budget_alert_threshold")
    val budgetAlertThreshold: Double? = null,

    // Change 149: budget tracking
    @ColumnInfo(name = "budget_amount")
    val budgetAmount: Double? = null,

    @ColumnInfo(name = "spent_amount")
    val spentAmount: Double? = null,
)
