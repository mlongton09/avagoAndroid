package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Change 20: work permit signatures
@Entity(
    tableName = "work_permit_signatures",
    indices = [
        Index(value = ["permit_id"]),
        Index(value = ["account_id"]),
    ]
)
data class WorkPermitSignatureEntity(
    @PrimaryKey
    @ColumnInfo(name = "signature_id")
    val signatureId: String,

    @ColumnInfo(name = "permit_id")
    val permitId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "signer_id")
    val signerId: String,

    @ColumnInfo(name = "signer_name")
    val signerName: String?,

    @ColumnInfo(name = "signature_url")
    val signatureUrl: String?,

    @ColumnInfo(name = "signed_at")
    val signedAt: Long?,

    @ColumnInfo(name = "all_signed", defaultValue = "0")
    val allSigned: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "server_version", defaultValue = "0")
    val serverVersion: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long? = null,
)
