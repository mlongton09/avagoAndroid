package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 34: Root Cause Analysis reports
@Entity(
    tableName = "rca_reports",
    indices = [
        Index(value = ["wo_id"]),
        Index(value = ["asset_id"]),
        Index(value = ["account_id"]),
    ]
)
data class RcaReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "report_id")
    val reportId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String?,

    @ColumnInfo(name = "asset_id")
    val assetId: String?,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "summary")
    val summary: String?,

    @ColumnInfo(name = "root_cause")
    val rootCause: String?,

    @ColumnInfo(name = "corrective_actions")
    val correctiveActions: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "author_id")
    val authorId: String?,

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
