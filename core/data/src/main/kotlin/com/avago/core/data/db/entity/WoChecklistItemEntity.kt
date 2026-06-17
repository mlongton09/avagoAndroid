package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wo_checklist_items",
    indices = [
        Index(value = ["wo_id"]),
    ]
)
data class WoChecklistItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "wo_id")
    val woId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,

    @ColumnInfo(name = "display_order")
    val displayOrder: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,

    // Change 71 / Change 42: procedure row type and metadata
    @ColumnInfo(name = "row_type")
    val rowType: String = "STEP",

    @ColumnInfo(name = "description")
    val rowDescription: String? = null,

    @ColumnInfo(name = "urls")
    val urlsJson: String? = null,

    // Change 19: checklist item response fields for procedure execution
    @ColumnInfo(name = "response")
    val response: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "pass_fail")
    val passFail: String? = null,

    @ColumnInfo(name = "signature_url")
    val signatureUrl: String? = null,

    // Change 64: audit trail
    @ColumnInfo(name = "created_by_id")
    val createdById: String? = null,

    @ColumnInfo(name = "updated_by_id")
    val updatedById: String? = null,
)
