package com.avago.core.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChatPresenceEntity(
    val userId: String,
    val accountId: String,
    val status: String,
    val lastSeenAt: Long?,
    val updatedAt: Long,
    // Change 68: rich custom status fields
    val statusEmoji: String? = null,
    val statusText: String? = null,
    val statusExpiresAt: Long? = null,
)
