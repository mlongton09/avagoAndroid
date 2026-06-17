package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bins",
    indices = [
        Index(value = ["location_id"]),
        Index(value = ["barcode"]),
    ]
)
data class BinEntity(
    @PrimaryKey
    @ColumnInfo(name = "bin_id")
    val binId: String,

    @ColumnInfo(name = "location_id")
    val locationId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "code")
    val code: String?,

    @ColumnInfo(name = "barcode")
    val barcode: String?,

    @ColumnInfo(name = "aisle")
    val aisle: String?,

    @ColumnInfo(name = "shelf")
    val shelf: String?,

    @ColumnInfo(name = "slot")
    val slot: String?,

    @ColumnInfo(name = "active", defaultValue = "1")
    val active: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    // Change 145: bin capacity and current count for fill % display
    @ColumnInfo(name = "capacity")
    val capacity: Long? = null,

    @ColumnInfo(name = "current_count")
    val currentCount: Long? = null,

    // Change 147: bin type classification (e.g. "shelf", "rack", "drawer")
    @ColumnInfo(name = "bin_type")
    val binType: String? = null,

    // Change 64: audit trail
    @ColumnInfo(name = "created_by_id")
    val createdById: String? = null,

    @ColumnInfo(name = "updated_by_id")
    val updatedById: String? = null,
)
