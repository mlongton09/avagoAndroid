package com.avago.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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

@Serializable
data class CustomFieldValue(
    val field_id: String,
    val name: String? = null,
    val type: String? = null,
    val value: JsonElement? = null,
)

// New: Meter-based PM triggers
@Serializable
data class MeterTrigger(
    val id: String,
    val name: String,
    val asset_id: String? = null,
    val trigger_value: Double? = null,
    val unit: String? = null,
    val template_id: String? = null,
)

// New: PM plans (calendar / meter / multi-interval)
@Serializable
data class PmPlan(
    val id: String,
    val name: String,
    val asset_id: String? = null,
    val rrule: String? = null,
    val schedule_type: String? = null,
    val intervals: JsonElement? = null,
    val next_run_at: String? = null,
)

// New: Permission sets
@Serializable
data class PermissionSet(
    val id: String,
    val name: String,
    val description: String? = null,
    val permissions: List<String> = emptyList(),
)
