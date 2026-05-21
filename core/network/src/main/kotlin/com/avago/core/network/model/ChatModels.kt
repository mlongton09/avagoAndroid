package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageResponse(
    val message_id: String,
    val thread_id: String,
    val sender_id: String,
    val sender_name: String? = null,
    val body_md: String,
    val body_preview: String? = null,
    val edited_at: String? = null, // ISO-8601
    val link_preview_title: String? = null,
    val link_preview_description: String? = null,
    val link_preview_image_url: String? = null,
    val link_preview_url: String? = null,
    val photo_url: String? = null,
    val reactions: String? = null,
    val server_version: Long = 0,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class ChatMessagesResponse(
    val messages: List<ChatMessageResponse>,
    val has_more: Boolean,
    val next_cursor: String? = null,
)

@Serializable
data class ChatThreadResponse(
    val thread_id: String,
    val account_id: String,
    val thread_type: String,
    val display_name: String? = null,
    val last_message_preview: String? = null,
    val last_message_at: String? = null,
    val unread_count: Int = 0,
    val subject_summary: String? = null,
    val server_version: Long = 0,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class SendMessageRequest(
    val body: String,
    val photo_url: String? = null,
)

@Serializable
data class EditMessageRequest(
    val body: String,
)

@Serializable
data class ReactMessageRequest(
    val emoji: String,
)

@Serializable
data class CreateThreadRequest(
    val thread_type: String, // "direct" or "group"
    val display_name: String? = null,
    val member_ids: List<String> = emptyList(),
)
