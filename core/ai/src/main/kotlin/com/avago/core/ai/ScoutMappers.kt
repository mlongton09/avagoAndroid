package com.avago.core.ai

import com.avago.core.network.model.ScoutExtractResponse
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun ScoutExtractResponse.toDomain(): ScoutResponse {
    val fields = when (val p = payload) {
        is JsonObject -> p.entries.associate { (k, v) ->
            k to when {
                v == JsonNull -> null
                v is JsonPrimitive -> v.content
                else -> v.toString()
            }
        }
        else -> emptyMap()
    }
    return ScoutResponse(
        targetScreen = fields["target_screen"],
        skillName = skill_name,
        fields = fields - "target_screen",
        envelopeId = request_id,
        actionCard = action_card?.let { ac ->
            ActionCard(
                title = ac.title,
                summary = ac.summary,
                skillName = ac.skill_name,
                dangerous = ac.dangerous,
                expiresAt = ac.expires_at,
            )
        },
    )
}
