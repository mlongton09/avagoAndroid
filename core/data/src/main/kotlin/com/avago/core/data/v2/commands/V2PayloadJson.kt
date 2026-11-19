package com.avago.core.data.v2.commands

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * JSON values for command payloads (sync-wire.md conventions): ids are UUID strings, decimals are
 * plain strings, timestamps are RFC 3339 UTC strings, dates are `YYYY-MM-DD`.
 */
object V2PayloadJson {
    fun value(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is BigDecimal -> JsonPrimitive(value.stripTrailingZeros().toPlainString())
        is Instant -> JsonPrimitive(value.toString())
        is LocalDate -> JsonPrimitive(value.toString())
        is V2WireEnum -> JsonPrimitive(value.wire)
        is V2PayloadStruct -> value.toJson()
        is Collection<*> -> JsonArray(value.map(::value))
        else -> error("unsupported payload value ${value::class.simpleName}")
    }

    /** An object of the non-null [pairs] (absent = not sent / server default). */
    fun objectOf(vararg pairs: Pair<String, Any?>): JsonObject =
        JsonObject(pairs.filter { it.second != null }.associate { it.first to value(it.second) })

    /** An object that keeps explicit nulls (update payloads: null clears the field). */
    fun objectWithNulls(pairs: Map<String, Any?>): JsonObject = JsonObject(pairs.mapValues { value(it.value) })

    fun timestamp(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)
}
