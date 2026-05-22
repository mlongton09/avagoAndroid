package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wo_assignments",
    indices = [
        Index(value = ["wo_id"]),
        Index(value = ["technician_id"]),
        Index(value = ["account_id"]),
    ]
)
data class WoAssignmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "assignment_id")
    val assignmentId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String = "",

    @ColumnInfo(name = "technician_id")
    val technicianId: String,

    @ColumnInfo(name = "assigned_by")
    val assignedBy: String?,

    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long,

    @ColumnInfo(name = "unassigned_at")
    val unassignedAt: Long?,

    @ColumnInfo(name = "scheduled_start")
    val scheduledStart: Long?,

    @ColumnInfo(name = "scheduled_end")
    val scheduledEnd: Long?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "ek_event_identifier")
    val ekEventIdentifier: String?,

    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
