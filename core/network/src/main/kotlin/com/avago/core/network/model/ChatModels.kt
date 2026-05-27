package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessageAuthorResponse(
    val id: String? = null,
    val display_name: String? = null,
)

@Serializable
data class ChatLinkPreviewResponse(
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image_url: String? = null,
)

@Serializable
data class ChatMessageResponse(
    val message_id: String,
    val thread_id: String,
    val author_id: String? = null,
    val author: ChatMessageAuthorResponse? = null,
    val body_md: String,
    val edited_at: String? = null,
    val link_preview: ChatLinkPreviewResponse? = null,
    val photo_url: String? = null,
    val reactions: String? = null,
    val server_version: Long = 0,
    val created_at: String,
    val updated_at: String,
    val parent_message_id: String? = null,
    val is_pinned: Boolean = false,
)

@Serializable
data class SendMessageEnvelope(
    val message: ChatMessageResponse,
)

@Serializable
data class ChatMessagesResponse(
    val messages: List<ChatMessageResponse> = emptyList(),
    val next_cursor: String? = null,
)

@Serializable
data class ChatThreadResponse(
    val thread_id: String,
    val account_id: String,
    @SerialName("type") val thread_type: String,
    // Server sends "name" for direct/group thread title (not "display_name")
    val name: String? = null,
    val last_message_preview: String? = null,
    // Server sends "last_activity_at" (not "last_message_at")
    @SerialName("last_activity_at") val last_activity_at: String? = null,
    val unread_count: Int = 0,
    // Server sends subject_summary as a JSON object, not a string
    val subject_summary: JsonElement? = null,
    val is_favorite: Boolean = false,
    // Members array present for direct/group threads — used to resolve display names
    val members: List<ChatMemberResponse> = emptyList(),
    val created_at: String,
)

@Serializable
data class ChatThreadsEnvelope(
    val threads: List<ChatThreadResponse> = emptyList(),
    val next_cursor: String? = null,
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
    val mention_push_enabled: Boolean = true,
    val broadcast_push_enabled: Boolean = true,
    val wo_push_enabled: Boolean = true,
    val team_room_push_enabled: Boolean = true,
    val reaction_to_you_push_enabled: Boolean = true,
)

@Serializable
data class ChatPrefsRequest(
    val notification_sound: Boolean? = null,
    val show_previews: Boolean? = null,
    val badge_count: Boolean? = null,
    val mention_push_enabled: Boolean? = null,
    val broadcast_push_enabled: Boolean? = null,
    val wo_push_enabled: Boolean? = null,
    val team_room_push_enabled: Boolean? = null,
    val reaction_to_you_push_enabled: Boolean? = null,
)

@Serializable
data class ChatRosterEntry(
    val user_id: String,
    val display_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val presence: String? = null,
    val role: String? = null,
)

@Serializable
data class ChatRosterEnvelope(
    val generated_at: String? = null,
    val members: List<ChatRosterEntry> = emptyList(),
)
