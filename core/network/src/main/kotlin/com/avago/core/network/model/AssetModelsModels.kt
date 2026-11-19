package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class FileUploadResponse(
    val file_upload_id: String,
    val account_id: String,
    val entity_id: String? = null,
    val entity_type: String? = null,
    val storage_key: String = "",
    val original_name: String? = null,
    val content_type: String? = null,
    val file_size_bytes: Long? = null,
    val upload_status: String = "pending",
    val created_by: String? = null,
    val created_at: String = "",
    val expires_at: String = "",
    val deleted_at: String? = null,
)

@Serializable
data class PresignedUploadRequest(
    val entity_id: String? = null,
    val entity_type: String? = null,
    val filename: String? = null,
    val content_type: String? = null,
)

@Serializable
data class PresignedUploadResponse(
    val file_upload_id: String,
    val upload_url: String,
    val storage_key: String,
    val expires_in_seconds: Int,
)
