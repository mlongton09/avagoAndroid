package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_orders")
data class WorkOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String?,

    @ColumnInfo(name = "location_id")
    val locationId: String?,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "priority")
    val priority: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "requester_id")
    val requesterId: String?,

    @ColumnInfo(name = "assigned_to")
    val assignedTo: String?,

    @ColumnInfo(name = "dispatcher_notes")
    val dispatcherNotes: String?,

    @ColumnInfo(name = "required_skills")
    val requiredSkills: String?,

    @ColumnInfo(name = "estimated_effort_minutes")
    val estimatedEffortMinutes: Long?,

    @ColumnInfo(name = "actual_effort_minutes")
    val actualEffortMinutes: Long?,

    @ColumnInfo(name = "failure_code")
    val failureCode: String?,

    @ColumnInfo(name = "completion_notes")
    val completionNotes: String?,

    @ColumnInfo(name = "parts_needed")
    val partsNeeded: String?,

    @ColumnInfo(name = "log_id")
    val logId: String?,

    @ColumnInfo(name = "due_date")
    val dueDate: Long?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long?,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,

    @ColumnInfo(name = "timer_started_at")
    val timerStartedAt: Long?,

    @ColumnInfo(name = "labor_cost")
    val laborCost: Double?,

    @ColumnInfo(name = "parts_cost")
    val partsCost: Double?,

    @ColumnInfo(name = "total_cost")
    val totalCost: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "base_amount")
    val baseAmount: Double?,

    @ColumnInfo(name = "exchange_rate_used")
    val exchangeRateUsed: Double?,

    @ColumnInfo(name = "attributes")
    val attributes: String?,

    @ColumnInfo(name = "created_by")
    val createdBy: String?,

    @ColumnInfo(name = "approval_state")
    val approvalState: String?,

    @ColumnInfo(name = "job_id")
    val jobId: String?,

    @ColumnInfo(name = "wo_kind")
    val woKind: String?,

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

    @ColumnInfo(name = "parent_wo_id")
    val parentWoId: String?,

    @ColumnInfo(name = "occurrence_date")
    val occurrenceDate: String?,

    @ColumnInfo(name = "schedule_id")
    val scheduleId: String?,

    @ColumnInfo(name = "last_completed_at")
    val lastCompletedAt: Long?,

    @ColumnInfo(name = "timezone")
    val timezone: String?,

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
