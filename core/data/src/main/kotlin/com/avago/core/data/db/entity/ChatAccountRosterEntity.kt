package com.avago.core.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChatAccountRosterEntity(
    val rosterId: String,
    val accountId: String,
    val userId: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val role: String?,
    val isActive: Boolean = true,
    val updatedAt: Long,
)
