package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 30: custom field definition (pull-only reference)
@Entity(
    tableName = "custom_field_definitions",
    indices = [
        Index(value = ["account_id", "entity_type"]),
    ]
)
data class CustomFieldDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "definition_id")
    val definitionId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "field_name")
    val fieldName: String,

    @ColumnInfo(name = "field_type")
    val fieldType: String,

    @ColumnInfo(name = "label")
    val label: String?,

    @ColumnInfo(name = "options_json")
    val optionsJson: String?,

    @ColumnInfo(name = "is_required", defaultValue = "0")
    val isRequired: Boolean = false,

    @ColumnInfo(name = "display_order")
    val displayOrder: Long = 0L,

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
