package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "label_templates",
    indices = [
        Index(value = ["account_id", "deleted_at"]),
        Index(value = ["template_type"])
    ]
)
data class LabelTemplateEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "template_type") val templateType: String? = null,
    @ColumnInfo(name = "content") val content: String? = null,
    @ColumnInfo(name = "width_mm") val widthMm: Double? = null,
    @ColumnInfo(name = "height_mm") val heightMm: Double? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "server_seq") val serverSeq: Long = 0L
)
