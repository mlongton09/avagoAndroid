package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tech_labor_rates")
data class TechLaborRateEntity(
    @PrimaryKey
    @ColumnInfo(name = "rate_id")
    val rateId: String,

    @ColumnInfo(name = "tech_id")
    val techId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "role_key")
    val roleKey: String?,

    @ColumnInfo(name = "hourly_rate")
    val hourlyRate: Double,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "effective_date")
    val effectiveDate: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,
)
