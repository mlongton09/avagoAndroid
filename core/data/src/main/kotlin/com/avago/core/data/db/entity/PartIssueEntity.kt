package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "part_issues")
data class PartIssueEntity(
    @PrimaryKey
    @ColumnInfo(name = "issue_id")
    val issueId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "location_id")
    val locationId: String?,

    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String?,

    @ColumnInfo(name = "to_location_id")
    val toLocationId: String?,

    @ColumnInfo(name = "issue_type")
    val issueType: String,

    @ColumnInfo(name = "issued_at")
    val issuedAt: Long,

    @ColumnInfo(name = "issued_by")
    val issuedBy: String?,

    @ColumnInfo(name = "reference_id")
    val referenceId: String?,

    @ColumnInfo(name = "reference_type")
    val referenceType: String?,

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
