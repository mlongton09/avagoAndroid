package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "last_server_seq", defaultValue = "0")
    val lastServerSeq: Long,

    @ColumnInfo(name = "last_sync_at", defaultValue = "0")
    val lastSyncAt: Long,
)
