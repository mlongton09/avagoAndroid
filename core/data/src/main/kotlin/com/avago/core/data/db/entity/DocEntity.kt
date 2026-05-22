package com.avago.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "docs",
    indices = [
        Index(value = ["account_id", "deleted_at"]),
        Index(value = ["asset_id"]),
    ]
)
data class DocEntity(
    @PrimaryKey
    @ColumnInfo(name = "doc_id")
    val docId: String,

    @ColumnInfo(name = "asset_id")
    val assetId: String?,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "doc_type")
    val docType: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String?,

    @ColumnInfo(name = "storage_key")
    val storageKey: String?,

    @ColumnInfo(name = "download_url")
    val downloadUrl: String?,

    @ColumnInfo(name = "ocr_raw_text")
    val ocrRawText: String?,

    @ColumnInfo(name = "ocr_extracted_json")
    val ocrExtractedJson: String?,

    @ColumnInfo(name = "vendor")
    val vendor: String?,

    @ColumnInfo(name = "total")
    val total: Double?,

    @ColumnInfo(name = "currency")
    val currency: String?,

    @ColumnInfo(name = "purchase_date")
    val purchaseDate: Long?,

    @ColumnInfo(name = "uploaded_by")
    val uploadedBy: String?,

    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long?,

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
