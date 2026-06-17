package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PhotoUploadUrlResponse(
    val upload_url: String,
    val storage_key: String,
)

@Serializable
data class PhotoResponse(
    val photo_id: String,
    val entity_id: String,
    val entity_type: String,
    val storage_key: String? = null,
    val download_url: String? = null,
    val sort_order: Int = 0,
    val created_at: Long = 0,
    // Change 99: photo thumbnails
    val thumbnail_url: String? = null,
    val thumbnail_width: Int? = null,
    val thumbnail_height: Int? = null,
)
