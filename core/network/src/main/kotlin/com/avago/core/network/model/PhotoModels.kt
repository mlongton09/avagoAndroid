package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PhotoUploadUrlResponse(
    val upload_url: String,
    val storage_key: String,
)
