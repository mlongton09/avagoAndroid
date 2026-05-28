package com.avago.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Wire models for the Scout AI endpoint.
 *
 * POST /ai/extract  (server: avagosvc/src/ai_bot/extraction/extract.rs)
 */

/** Sent to POST /ai/extract. Mirrors iOS AIExtractRequest. */
@Serializable
data class ScoutExtractRequest(
    val transcript: String,
    val screen_context: ScoutScreenContext,
    val skill_hint: String? = null,
    val thread_id: String? = null,
)

/**
 * Screen-context envelope. Must include [account_id] for the server's
 * cross-account gate. Extra fields (recent entities, current screen)
 * are passed as top-level fields and tolerated by the server's
 * `#[serde(flatten)] extra` bag.
 */
@Serializable
data class ScoutScreenContext(
    val account_id: String,
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
data class AiActionCard(
    val title: String,
    val summary: String? = null,
    val skill_name: String,
    val dangerous: Boolean = false,
    val expires_at: Long? = null,
)

/**
 * 200-OK shape from POST /ai/extract.
 *
 * [payload] is the model's raw JSON output for the matched skill.
 * For form-fill skills it's an object whose keys are form field names.
 * [action_card] is non-null for state-changing skills that require confirmation.
 */
@Serializable
data class ScoutExtractResponse(
    val request_id: String,
    val skill_name: String,
    val payload: JsonElement = JsonNull,
    val action_card: AiActionCard? = null,
)

/** Wire model for GET /accounts/{accountId}/ai/skills */
@Serializable
data class AiSkillResponse(
    @SerialName("name") val skill_id: String,
    @SerialName("display_name") val name: String? = null,
    val description: String? = null,
    val state_changing: Boolean = false,
    val example_phrasings: List<String> = emptyList(),
    val input_schema: String? = null,
)

@Serializable
data class AiSkillsEnvelope(val skills: List<AiSkillResponse> = emptyList())
