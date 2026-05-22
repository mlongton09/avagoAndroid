package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vendors",
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["vendor_code"]),
    ]
)
data class VendorEntity(
    @PrimaryKey
    @ColumnInfo(name = "vendor_id")
    val vendorId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "vendor_code")
    val vendorCode: String?,

    @ColumnInfo(name = "contact_name")
    val contactName: String?,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "phone")
    val phone: String?,

    @ColumnInfo(name = "fax")
    val fax: String?,

    @ColumnInfo(name = "website")
    val website: String?,

    @ColumnInfo(name = "address")
    val address: String?,

    @ColumnInfo(name = "account_number")
    val accountNumber: String?,

    @ColumnInfo(name = "payment_terms")
    val paymentTerms: String?,

    @ColumnInfo(name = "default_currency")
    val defaultCurrency: String?,

    @ColumnInfo(name = "tax_id")
    val taxId: String?,

    @ColumnInfo(name = "rating")
    val rating: Double?,

    @ColumnInfo(name = "preferred", defaultValue = "0")
    val preferred: Boolean = false,

    @ColumnInfo(name = "active", defaultValue = "1")
    val active: Boolean = true,

    @ColumnInfo(name = "qbo_vendor_id")
    val qboVendorId: String?,

    @ColumnInfo(name = "notes")
    val notes: String?,

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
