package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 23: work permits CRUD
@Entity(
    tableName = "work_permits",
    indices = [
        Index(value = ["wo_id"]),
        Index(value = ["account_id"]),
    ]
)
data class WorkPermitEntity(
    @PrimaryKey
    @ColumnInfo(name = "permit_id")
    val permitId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "permit_type")
    val permitType: String?,

    @ColumnInfo(name = "required_approvers")
    val requiredApprovers: String?,

    @ColumnInfo(name = "approved_by")
    val approvedBy: String?,

    @ColumnInfo(name = "approved_at")
    val approvedAt: Long?,

    @ColumnInfo(name = "rejected_by")
    val rejectedBy: String?,

    @ColumnInfo(name = "rejected_at")
    val rejectedAt: Long?,

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
