package com.avago.core.network.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Lenient readers for server JSON: a missing key, null or wrong type reads as null.

fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotEmpty() }

fun JsonObject.long(key: String): Long? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.content.toLongOrNull()
}

fun JsonObject.int(key: String): Int? = long(key)?.toInt()

fun JsonObject.double(key: String): Double? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.doubleOrNull ?: p.content.toDoubleOrNull()
}

fun JsonObject.bool(key: String): Boolean? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.booleanOrNull
}

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

fun JsonObject.objs(key: String): List<JsonObject> = arr(key).filterIsInstance<JsonObject>()

fun JsonObject.strs(key: String): List<String> =
    arr(key).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }

/**
 * Builds a request body, dropping Kotlin nulls so optional fields are omitted; pass [JsonNull]
 * to send an explicit null.
 */
fun jsonBody(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
    buildMap {
        for ((k, v) in pairs) {
            val el = toJson(v) ?: continue
            put(k, el)
        }
    },
)

fun toJson(v: Any?): JsonElement? = when (v) {
    null -> null
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.mapNotNull { (k, e) -> toJson(e)?.let { k.toString() to it } }.toMap())
    is Iterable<*> -> JsonArray(v.map { toJson(it) ?: JsonNull })
    else -> JsonPrimitive(v.toString())
}
