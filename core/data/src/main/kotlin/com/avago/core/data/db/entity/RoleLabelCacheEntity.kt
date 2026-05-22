package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "role_label_cache")
data class RoleLabelCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "role_key")
    val roleKey: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "color")
    val color: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
