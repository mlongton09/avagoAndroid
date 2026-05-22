package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["entity_id", "entity_type"]),
        Index(value = ["starts_at"]),
    ]
)
data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String?,

    @ColumnInfo(name = "entity_type")
    val entityType: String?,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "event_type")
    val eventType: String?,

    @ColumnInfo(name = "starts_at")
    val startsAt: Long?,

    @ColumnInfo(name = "ends_at")
    val endsAt: Long?,

    @ColumnInfo(name = "all_day", defaultValue = "0")
    val allDay: Boolean,

    @ColumnInfo(name = "location_id")
    val locationId: String?,

    @ColumnInfo(name = "created_by")
    val createdBy: String?,

    @ColumnInfo(name = "attendees")
    val attendees: String?,

    @ColumnInfo(name = "ek_event_identifier")
    val ekEventIdentifier: String?,

    @ColumnInfo(name = "attributes")
    val attributes: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
