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
    val parent_message_id: String? = null,
    val is_pinned: Boolean = false,
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

@Serializable
data class ChatMemberResponse(
    val user_id: String,
    val display_name: String? = null,
    val role: String? = null,
    val joined_at: Long = 0,
)

@Serializable
data class ChatPageResponse(
    val items: List<ChatMessageResponse> = emptyList(),
    val cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class LinkPreviewResponse(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val image_url: String? = null,
    val site_name: String? = null,
)

@Serializable
data class ChatMediaPresignResponse(
    val upload_url: String,
    val media_url: String,
    val expires_at: Long,
)

@Serializable
data class ChatSyncResponse(
    val ops: List<ChatSyncOp> = emptyList(),
    val cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class ChatSyncOp(
    val op: String,  // "upsert", "delete"
    val entity_type: String,
    val payload: String? = null,  // JSON string
)

@Serializable
data class ChatPrefsResponse(
    val notification_sound: Boolean = true,
    val show_previews: Boolean = true,
    val badge_count: Boolean = true,
)

@Serializable
data class ChatPrefsRequest(
    val notification_sound: Boolean? = null,
    val show_previews: Boolean? = null,
    val badge_count: Boolean? = null,
)

@Serializable
data class ChatRosterEntry(
    val user_id: String,
    val display_name: String? = null,
    val is_online: Boolean = false,
    val last_seen_at: Long? = null,
)
