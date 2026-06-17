package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["entity_id", "entity_type"]),
    ]
)
data class PhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "photo_id")
    val photoId: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "storage_key")
    val storageKey: String?,

    @ColumnInfo(name = "download_url")
    val downloadUrl: String?,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Long,

    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    /** Absolute path to a locally-captured image file. Null once evicted by PhotoCacheSweeper. */
    @ColumnInfo(name = "local_path", defaultValue = "NULL")
    val localPath: String? = null,

    // Change 99: photo thumbnails
    @ColumnInfo(name = "thumbnail_url", defaultValue = "NULL")
    val thumbnailUrl: String? = null,

    @ColumnInfo(name = "thumbnail_width", defaultValue = "NULL")
    val thumbnailWidth: Int? = null,

    @ColumnInfo(name = "thumbnail_height", defaultValue = "NULL")
    val thumbnailHeight: Int? = null,
)
