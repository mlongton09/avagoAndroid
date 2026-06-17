package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 110/117: persistent sync conflict storage
@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["resolution_status"]),
    ]
)
data class SyncConflictEntity(
    @PrimaryKey
    @ColumnInfo(name = "conflict_id")
    val conflictId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "client_payload")
    val clientPayload: String?,

    @ColumnInfo(name = "server_payload")
    val serverPayload: String?,

    @ColumnInfo(name = "conflict_resolution")
    val conflictResolution: String?,

    @ColumnInfo(name = "resolution_status")
    val resolutionStatus: String = "PENDING",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
