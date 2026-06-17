package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 124/125: owner assignments with secondary owner and fallback
@Entity(
    tableName = "owner_assignments",
    indices = [
        Index(value = ["resource_type", "resource_id"]),
        Index(value = ["owner_user_id"]),
    ]
)
data class OwnerAssignmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "assignment_id")
    val assignmentId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "resource_type")
    val resourceType: String,

    @ColumnInfo(name = "resource_id")
    val resourceId: String,

    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,

    // Change 125: secondary owner
    @ColumnInfo(name = "secondary_owner_id")
    val secondaryOwnerId: String?,

    @ColumnInfo(name = "fallback_enabled", defaultValue = "0")
    val fallbackEnabled: Boolean = false,

    @ColumnInfo(name = "role")
    val role: String?,

    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long?,

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
