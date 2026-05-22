package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["sync_status", "created_at"]),
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "queue_id")
    val queueId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "server_version")
    val serverVersion: Long?,

    @ColumnInfo(name = "payload")
    val payload: String?,

    @ColumnInfo(name = "sync_status", defaultValue = "pending")
    val syncStatus: String,

    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Long,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
