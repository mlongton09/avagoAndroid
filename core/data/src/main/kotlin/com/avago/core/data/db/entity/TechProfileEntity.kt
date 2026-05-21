package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tech_profiles")
data class TechProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "tech_id")
    val techId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "skills")
    val skills: String?,

    @ColumnInfo(name = "certifications")
    val certifications: String?,

    @ColumnInfo(name = "hourly_rate")
    val hourlyRate: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "availability")
    val availability: String?,

    @ColumnInfo(name = "speed_factor")
    val speedFactor: Double?,

    @ColumnInfo(name = "current_location_lat")
    val currentLocationLat: Double?,

    @ColumnInfo(name = "current_location_lng")
    val currentLocationLng: Double?,

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
