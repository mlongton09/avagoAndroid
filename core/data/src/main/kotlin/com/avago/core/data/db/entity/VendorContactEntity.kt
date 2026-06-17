package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendor_contacts",
    indices = [
        Index(value = ["vendor_id"]),
        Index(value = ["account_id"]),
    ]
)
data class VendorContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "contact_id")
    val contactId: String,

    @ColumnInfo(name = "vendor_id")
    val vendorId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "phone")
    val phone: String?,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "is_primary", defaultValue = "0")
    val isPrimary: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long,

    @ColumnInfo(name = "seq")
    val seq: Long?,
)
