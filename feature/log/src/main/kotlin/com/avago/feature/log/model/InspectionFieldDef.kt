package com.avago.feature.log.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a single field in an inspection form config, parsed from
 * a ConfigEntity value JSON array.
 *
 * Example JSON element:
 * { "key": "engine_oil", "label": "Engine Oil", "type": "pass_fail" }
 * { "key": "notes", "label": "Notes", "type": "text" }
 * { "key": "tire_condition", "label": "Tire Condition", "type": "select",
 *   "options": ["Good","Fair","Poor"] }
 */
data class InspectionFieldDef(
    val key: String,
    val label: String,
    val type: String, // "checkbox" | "select" | "text" | "number" | "pass_fail"
    val options: List<String> = emptyList(), // for "select" type
    val required: Boolean = false,
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseInspectionFields(json: String?): List<InspectionFieldDef> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = lenientJson.parseToJsonElement(json).jsonArray
        array.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                val key = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val label = obj["label"]?.jsonPrimitive?.content ?: key
                val type = obj["type"]?.jsonPrimitive?.content ?: "text"
                val options = obj["options"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content }
                    ?: emptyList()
                val required = obj["required"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                InspectionFieldDef(key, label, type, options, required)
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
