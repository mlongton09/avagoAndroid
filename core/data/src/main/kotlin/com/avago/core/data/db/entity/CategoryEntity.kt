package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 89/93/94: categories with color/icon, default SLA, and hierarchy
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["account_id", "entity_type"]),
        Index(value = ["parent_category_id"]),
    ]
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "category_id")
    val categoryId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    // Change 89: entity type this category applies to (e.g. "work_order", "asset")
    @ColumnInfo(name = "entity_type")
    val entityType: String?,

    @ColumnInfo(name = "color")
    val color: String?,

    @ColumnInfo(name = "icon")
    val icon: String?,

    // Change 93: default priority and SLA hours
    @ColumnInfo(name = "default_priority")
    val defaultPriority: String?,

    @ColumnInfo(name = "default_sla_hours")
    val defaultSlaHours: Double?,

    // Change 94: parent for hierarchy
    @ColumnInfo(name = "parent_category_id")
    val parentCategoryId: String?,

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
