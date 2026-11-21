package com.avago.core.data.db.entity

import kotlinx.serialization.Serializable

@Serializable
data class ChatThreadEntity(
    val threadId: String,
    val accountId: String,
    val threadType: String, // "team" | "direct" | "group" | "wo" | "asset"
    val displayName: String?,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0,
    val subjectSummary: String?, // JSON blob
    val serverVersion: Long = 0,
    val deletedAt: Long? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val notificationPref: String? = null, // "all" | "mentions_only" | "mute"
    val createdAt: Long,
    val updatedAt: Long,
    // Change 70: thread topic and description
    val topic: String? = null,
    val description: String? = null,
)
