package com.avago.core.network.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

// New: Custom field definitions and values (Changes 141+)
@Serializable
data class CustomFieldDef(
    val id: String,
    val name: String,
    val field_type: String,
    val entity_type: String,
    val options: List<String> = emptyList(),
)

// New: PM plans (calendar / meter / multi-interval).
// Svc wire shape: `plan_id`, `title`, UPPERCASE `schedule_type`, and an `attributes` JSON object
// that must round-trip untouched. Legacy `id` / `name` are still accepted on read.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PmPlan(
    @SerialName("plan_id") @JsonNames("id") val id: String,
    @SerialName("title") @JsonNames("name") val name: String,
    val asset_id: String? = null,
    val rrule: String? = null,
    val schedule_type: String? = null,
    val intervals: JsonElement? = null,
    val next_run_at: String? = null,
    val attributes: JsonObject? = null,
) {
    /** Payload for create/PATCH: Svc expects UPPERCASE schedule_type; attributes are preserved. */
    fun forWrite(): PmPlan = copy(schedule_type = schedule_type?.uppercase())
}

// New: Permission sets
@Serializable
data class PermissionSet(
    val id: String,
    val name: String,
    val description: String? = null,
    val permissions: List<String> = emptyList(),
)
