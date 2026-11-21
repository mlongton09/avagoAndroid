package com.avago.core.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChatMentionEntity(
    val mentionId: String,
    val threadId: String,
    val messageId: String,
    val accountId: String,
    val mentionedBy: String?,
    val isRead: Boolean = false,
    val createdAt: Long,
)
