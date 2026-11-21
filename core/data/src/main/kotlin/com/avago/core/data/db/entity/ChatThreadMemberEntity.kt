package com.avago.core.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChatThreadMemberEntity(
    val threadId: String,
    val userId: String,
    val accountId: String,
    val displayName: String?,
    val role: String?,
    val joinedAt: Long?,
    val leftAt: Long?,
    val isMuted: Boolean = false,
)
