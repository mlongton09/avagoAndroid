package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wo_assignments")
data class WoAssignmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "assignment_id")
    val assignmentId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "technician_id")
    val technicianId: String,

    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long,

    @ColumnInfo(name = "unassigned_at")
    val unassignedAt: Long?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
