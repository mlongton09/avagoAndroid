package com.avago.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageTemplate(
    val template_id: String,
    val user_id: String? = null,
    val account_id: String,
    val title: String,
    val body_md: String,
    val sort_order: Int = 0,
    val is_active: Boolean = true,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class CreateTemplateRequest(
    val title: String,
    val body_md: String,
    val sort_order: Int? = null,
)

@Serializable
data class ChatWebhook(
    val webhook_id: String,
    val account_id: String,
    val thread_id: String,
    val name: String,
    val is_active: Boolean = true,
    val created_by: String,
    val last_used_at: String? = null,
    val message_count: Int = 0,
    val created_at: String = "",
)

@Serializable
data class CreateWebhookRequest(
    val name: String,
    val thread_id: String,
    val allowed_ips: List<String>? = null,
)

@Serializable
data class CreateWebhookResponse(
    val webhook_id: String,
    val webhook_url: String,
    val token: String,
)

@Serializable
data class SyncConflict(
    val id: String,
    val account_id: String,
    val entity_type: String,
    val entity_id: String,
    val server_payload: kotlinx.serialization.json.JsonObject,
    val client_payload: kotlinx.serialization.json.JsonObject,
    val status: String = "OPEN",
    val resolved_by_user_id: String? = null,
    val resolved_at: String? = null,
    val created_at: String = "",
)

@Serializable
data class SlashCommandRequest(
    val thread_id: String,
    val command: String,
    val args: String,
)

@Serializable
data class GenerateCycleCountsRequest(
    val scheduled_date: String,
    val include_overdue: Boolean = false,
    val preview_only: Boolean = false,
    val abc_classes: List<String>? = null,
    val location_ids: List<String>? = null,
    val last_count_before: String? = null,
)

@Serializable
data class CopyTemplateRequest(
    val title: String? = null,
)
