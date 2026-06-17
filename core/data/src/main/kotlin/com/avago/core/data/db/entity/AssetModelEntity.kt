package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 40/41/92: asset model definitions with recommended parts and serial number pattern
@Entity(
    tableName = "asset_models",
    indices = [Index(value = ["account_id"])]
)
data class AssetModelEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "manufacturer")
    val manufacturer: String?,

    @ColumnInfo(name = "default_procedure_template_id")
    val defaultProcedureTemplateId: String?,

    // Change 41: recommended parts as JSON array
    @ColumnInfo(name = "recommended_parts")
    val recommendedParts: String?,

    // Change 92: serial number validation pattern + help text
    @ColumnInfo(name = "serial_number_pattern")
    val serialNumberPattern: String?,

    @ColumnInfo(name = "help_text")
    val helpText: String?,

    @ColumnInfo(name = "examples")
    val examples: String?,

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
