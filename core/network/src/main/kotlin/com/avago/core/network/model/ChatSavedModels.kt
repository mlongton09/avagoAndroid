package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageSave(
    val user_id: String,
    val message_id: String,
    val note: String? = null,
    val saved_at: String = "",
)

@Serializable
data class ChatUserStatus(
    val user_id: String,
    val status_emoji: String? = null,
    val status_text: String? = null,
    val status_expires_at: String? = null,
    val status_preset: String? = null,
)

@Serializable
data class ChatScheduledMessage(
    val scheduled_message_id: String,
    val thread_id: String,
    val account_id: String,
    val author_id: String,
    val body_md: String,
    val image_keys: List<String> = emptyList(),
    val send_at: String,
    val sent_at: String? = null,
    val cancelled_at: String? = null,
    val created_at: String = "",
)

@Serializable
data class SetStatusRequest(
    val emoji: String? = null,
    val text: String? = null,
    val expires_at: String? = null,
    val preset: String? = null,
)

@Serializable
data class UpdateScheduledMessageRequest(
    val body_md: String? = null,
    val send_at: String? = null,
)

@Serializable
data class SaveMessageRequest(
    val note: String? = null,
)

@Serializable
data class SetTopicRequest(
    val topic: String,
)

@Serializable
data class SetDescriptionRequest(
    val description: String,
)
