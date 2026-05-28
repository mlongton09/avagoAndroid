package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessageAuthorResponse(
    val id: String? = null,
    val display_name: String? = null,
    val avatar_url: String? = null,
)

@Serializable
data class ChatLinkPreviewResponse(
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val image_url: String? = null,
    val site_name: String? = null,
)

@Serializable
data class ChatMentionRequest(
    val kind: String,         // "user" | "all" | "here"
    val user_id: String? = null,
    val display_name: String? = null,
)

@Serializable
data class ChatMessageResponse(
    val message_id: String,
    val thread_id: String,
    val author_id: String? = null,
    val author: ChatMessageAuthorResponse? = null,
    val body_md: String,
    val body_html: String? = null,
    val edited_at: String? = null,
    val link_preview: ChatLinkPreviewResponse? = null,
    // Single attachment (legacy) — prefer image_urls when both present
    val photo_url: String? = null,
    // Multi-image support — matches iOS image_urls array
    val image_urls: List<String> = emptyList(),
    // Mention arrays — parallel: mentioned_user_ids[i] corresponds to mention_kinds[i]
    val mentioned_user_ids: List<String> = emptyList(),
    val mention_kinds: List<String> = emptyList(),
    // System messages (e.g. "member_added", "thread_renamed")
    val is_system: Boolean = false,
    val system_kind: String? = null,
    val system_payload: String? = null, // raw JSON string
    // Subthread reply counts — shown as "N replies" preview beneath messages
    val reply_count: Int = 0,
    val latest_reply_at: String? = null,
    // Delivery/read tracking (counts only — no per-user lists on wire)
    val delivered_by_count: Int = 0,
    val read_by_count: Int = 0,
    val read_by_total: Int = 0,
    // Reaction counts {"👍": 3} and viewer's own reactions ["👍"]
    val reaction_counts: Map<String, Int> = emptyMap(),
    val my_reactions: List<String> = emptyList(),
    // Needs-reply flag and idempotency key
    val needs_reply: Boolean = false,
    val client_ref: String? = null,
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
    val image_urls: List<String>? = null,
    val mentions: List<ChatMentionRequest>? = null,
    val client_ref: String? = null,
    val needs_reply: Boolean? = null,
    val parent_message_id: String? = null,
    val subthread_root_message_id: String? = null,
    val quick_reply_kind: String? = null,
    val link_preview: ChatLinkPreviewResponse? = null,
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
    // Server field is "next_cursor", not "cursor".
    @SerialName("next_cursor") val cursor: String? = null,
    val has_more: Boolean = false,
)

// Server sends: {"kind": "thread.upserted"|"message.created"|"message.updated"|"message.deleted", ...}
// All extra fields are optional so one class covers every op kind.
@Serializable
data class ChatSyncOp(
    val kind: String = "",
    // thread.upserted
    val thread: JsonElement? = null,
    // message.created / message.updated
    val message: JsonElement? = null,
    // message.deleted
    val message_id: String? = null,
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
    val quiet_hours_start: String? = null,
    val quiet_hours_end: String? = null,
    val quiet_hours_timezone: String? = null,
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
    val quiet_hours_start: String? = null,
    val quiet_hours_end: String? = null,
    val quiet_hours_timezone: String? = null,
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
