package com.avago.feature.assets.model

import java.time.Year
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val AUTO_CONFIDENCE = 0.8
private const val MAX_METADATA_BYTES = 200
private const val MIN_ASSET_YEAR = 1886

/** Raw authoritative metadata; no local category catalog or type-to-category guesses. */
data class AssetTaxonomy(val version: Long, val categories: List<JsonObject>) {
    fun root(id: String) = categories.find { it.text("id") == id }
    fun leaves(id: String) = root(id)?.get("subcategories")?.jsonArray.orEmpty().map { it.jsonObject }
    fun valid(value: JsonObject?): Boolean = value != null &&
        value.keys == setOf("category_id", "subcategory_id", "taxonomy_version", "source") &&
        (value["taxonomy_version"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull == version &&
        value.text("source") in setOf("manual", "qwen") &&
        leaves(value.text("category_id")).any { it.text("id") == value.text("subcategory_id") }
    fun legacy(value: JsonObject?) =
        value?.let { root(it.text("category_id"))?.text("legacy_marketplace_category") }.orEmpty()
    fun selection(parent: String, leaf: String, source: String = "manual") = buildJsonObject {
        put("category_id", parent); put("subcategory_id", leaf)
        put("taxonomy_version", version); put("source", source)
    }
    fun acceptSuggestion(current: JsonObject?, response: JsonObject): JsonObject? {
        val confidence = (response["confidence"] as? JsonPrimitive)?.doubleOrNull ?: Double.NaN
        val ready = confidence.isFinite() && confidence in AUTO_CONFIDENCE..1.0 &&
            (response["requires_review"] as? JsonPrimitive)?.booleanOrNull == false
        val keys = setOf("category_id", "subcategory_id", "taxonomy_version", "source")
        val candidate = JsonObject(response.filterKeys { it in keys })
        return if (current?.text("source") == "manual" || !ready) current
        else candidate.takeIf { valid(it) && it.text("source") == "qwen" } ?: current
    }
    companion object {
        fun parse(o: JsonObject) = AssetTaxonomy(o.getValue("version").jsonPrimitive.long,
            o.getValue("categories").jsonArray.map { it.jsonObject })
    }
}

fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.localizedLabel(german: Boolean): String =
    (get("label") as? JsonObject)?.text(if (german) "de" else "en").orEmpty().ifBlank { text("id") }

/** General metadata only. Classification and canonical identifier policy are separate checks. */
fun validAssetMetadata(name: String, year: String): Boolean =
    name.trim().let { trimmed ->
        trimmed.isNotBlank() && trimmed.toByteArray(Charsets.UTF_8).size <= MAX_METADATA_BYTES &&
            name.none(Char::isISOControl) && validAssetYear(year)
    }

private fun validAssetYear(year: String): Boolean = year.isBlank() ||
    (Regex("[0-9]{4}").matches(year.trim()) && year.trim().toInt() in MIN_ASSET_YEAR..(Year.now().value + 1))

/** Snapshot fence shared by asynchronous native editors. Manual choices win even for identical input. */
data class AssetSuggestionTarget(val account: String, val row: String, val name: String, val type: String) {
    fun matches(current: AssetSuggestionTarget?, classification: JsonObject?): Boolean =
        this == current && classification?.text("source") != "manual"
}

fun mergeAssetAttributes(original: JsonObject, patch: JsonObject): JsonObject = JsonObject(original + patch)

/** Refuse an unsafe non-object legacy patch instead of silently erasing its unknown data. */
fun decodeAssetAttributes(value: String?): JsonObject {
    val parsed = value?.takeIf(String::isNotBlank)?.let(Json::parseToJsonElement)
    require(parsed == null || parsed == JsonNull || parsed is JsonObject) { "asset_attributes_invalid" }
    return parsed as? JsonObject ?: JsonObject(emptyMap())
}
