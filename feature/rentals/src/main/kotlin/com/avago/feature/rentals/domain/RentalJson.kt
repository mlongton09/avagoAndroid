package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.Money
import com.avago.core.data.rental.RentalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** Shared JSON codec for rental rows (lenient: the API grows additively). */
val RentalJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

fun parseObj(raw: String?): JsonObject? =
    raw?.let { runCatching { RentalJson.parseToJsonElement(it).jsonObject }.getOrNull() }

fun JsonObject.str(key: String): String? = when (val v = this[key]) {
    null, is JsonNull -> null
    is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotEmpty() }
    else -> v.toString()
}

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
    it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull()
}

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let {
    it.longOrNull ?: it.contentOrNull?.toBigDecimalOrNull()?.toLong()
}

fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let {
    it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull()
}

fun JsonObject.instant(key: String): Instant? = RentalTime.parseInstant(str(key))

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()

fun JsonObject.objs(key: String): List<JsonObject> = arr(key).mapNotNull { it as? JsonObject }

fun JsonObject.strings(key: String): List<String> =
    arr(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

fun JsonObject.money(minorKey: String, currency: String?): Money? = Money.of(long(minorKey), currency)

/** The record's JSON (empty object when unreadable — never throws in UI code). */
val RentalRecordEntity.obj: JsonObject get() = parseObj(json) ?: JsonObject(emptyMap())
