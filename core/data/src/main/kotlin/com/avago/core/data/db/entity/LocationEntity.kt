package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["account_id", "deleted_at"]),
    ]
)
data class LocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "location_id")
    val locationId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "address")
    val address: String?,

    @ColumnInfo(name = "city")
    val city: String?,

    @ColumnInfo(name = "state")
    val state: String?,

    @ColumnInfo(name = "postal_code")
    val postalCode: String?,

    @ColumnInfo(name = "country")
    val country: String?,

    @ColumnInfo(name = "latitude")
    val latitude: Double?,

    @ColumnInfo(name = "longitude")
    val longitude: Double?,

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
