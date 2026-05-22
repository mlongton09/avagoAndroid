package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bins",
    indices = [
        Index(value = ["location_id"]),
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

    @ColumnInfo(name = "aisle")
    val aisle: String?,

    @ColumnInfo(name = "shelf")
    val shelf: String?,

    @ColumnInfo(name = "slot")
    val slot: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,
)
