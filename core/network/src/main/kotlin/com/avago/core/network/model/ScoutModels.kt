package com.avago.core.network.model

import kotlinx.serialization.Serializable

/**
 * Wire models for the Scout AI endpoint.
 *
 * POST /accounts/:accountId/ai/scout
 */
@Serializable
data class ScoutQueryRequest(
    val query: String,
    val recent_entities: List<ScoutEntityDto> = emptyList(),
    val current_screen: String? = null,
)

@Serializable
data class ScoutEntityDto(
    val type: String,
    val id: String,
    val display_name: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class ScoutQueryResponse(
    val target_screen: String? = null,
    val fields: Map<String, String?> = emptyMap(),
    val envelope_id: String,
    val message: String? = null,
)

/** Wire model for GET /accounts/{accountId}/ai/skills */
@Serializable
data class AiSkillResponse(
    val skill_id: String,
    val name: String,
    val description: String? = null,
    val input_schema: String? = null,
)
