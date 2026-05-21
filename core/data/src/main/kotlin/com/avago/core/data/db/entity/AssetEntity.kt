package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey
    @ColumnInfo(name = "asset_id")
    val assetId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "make")
    val make: String?,

    @ColumnInfo(name = "model")
    val model: String?,

    @ColumnInfo(name = "year")
    val year: Long?,

    @ColumnInfo(name = "asset_type")
    val assetType: String?,

    @ColumnInfo(name = "meter_type")
    val meterType: String?,

    @ColumnInfo(name = "avatar_color")
    val avatarColor: String?,

    @ColumnInfo(name = "avatar_initial")
    val avatarInitial: String?,

    @ColumnInfo(name = "address_line1")
    val addressLine1: String?,

    @ColumnInfo(name = "address_line2")
    val addressLine2: String?,

    @ColumnInfo(name = "city")
    val city: String?,

    @ColumnInfo(name = "state")
    val state: String?,

    @ColumnInfo(name = "postal_code")
    val postalCode: String?,

    @ColumnInfo(name = "country")
    val country: String?,

    @ColumnInfo(name = "location_id")
    val locationId: String?,

    @ColumnInfo(name = "attributes")
    val attributes: String?,

    @ColumnInfo(name = "is_fre_sample")
    val isFreSample: Boolean,

    @ColumnInfo(name = "parent_asset_id")
    val parentAssetId: String?,

    @ColumnInfo(name = "path")
    val path: String?,

    @ColumnInfo(name = "depth")
    val depth: Long,

    @ColumnInfo(name = "child_count")
    val childCount: Long,

    @ColumnInfo(name = "is_rental")
    val isRental: Boolean,

    @ColumnInfo(name = "rental_rate")
    val rentalRate: Double?,

    @ColumnInfo(name = "rental_rate_unit")
    val rentalRateUnit: String?,

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
