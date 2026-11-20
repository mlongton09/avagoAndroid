package com.avago.core.data.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** One field a refused edit wanted to change, with the value the server row has now. */
data class ConflictField(
    /** Wire field name, e.g. `due_at`. */
    val name: String,
    /** The edit's value as text; null when the edit cleared the field. */
    val mine: String?,
    /** The server row's value as text; null when it is empty or the snapshot lacks it. */
    val server: String?,
)

/**
 * The fields of a conflicting edit side by side with the server row: for an update payload
 * (`{"id", "set", "clear"}`) the `set` fields and the cleared fields; for any other payload its
 * top-level fields except `id`. Unparseable JSON yields no fields.
 */
fun SyncConflict.fieldDiffs(): List<ConflictField> {
    val payload = parse(clientPayload) ?: return emptyList()
    val server = serverSnapshot?.let(::parse)
    val set = payload["set"] as? JsonObject
    val mine: List<Pair<String, String?>> = if (set != null) {
        set.map { (k, v) -> k to text(v) } +
            (payload["clear"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.map { it to null }
    } else {
        payload.filterKeys { it != "id" }.map { (k, v) -> k to text(v) }
    }
    return mine.map { (name, value) -> ConflictField(name, value, server?.get(name)?.let(::text)) }
}

private fun parse(raw: String): JsonObject? = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()

private fun text(value: JsonElement): String? = when (value) {
    JsonNull -> null
    is JsonPrimitive -> value.content
    else -> value.toString()
}
