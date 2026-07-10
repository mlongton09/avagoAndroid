package com.avago.feature.log.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Full-fidelity model for the inspection checklist JSON served by the backend
 * (`type='Inspection'` configs), matching the same `inspectionChecklist` shape
 * the iOS client consumes (groups -> sections -> items). This mirrors iOS's
 * `AVInspectionFormView` data model so Android can render the identical form
 * structure (groups/sections/notes/guide text) instead of the old flat
 * key/label/options list in [InspectionFieldDef].
 *
 * Sample shape (see C:\avagosvc\DBSetup\seed_configs.sql):
 * {
 *   "inspectionChecklist": {
 *     "title": "insp.checklist.light_vehicle",
 *     "groups": [{
 *       "id": "basics", "title": "insp.group.basics",
 *       "sections": [{
 *         "id": "tires_wheels", "title": "insp.section.tires_wheels",
 *         "items": [
 *           {"id":"tires_02","text":"insp.item.tires_02","type":"select",
 *            "options":["insp.opt.normal","insp.opt.monitor","insp.opt.needs_repair"],
 *            "note":"insp.item.tires_02.note"},
 *           {"id":"...","type":"corner-select","options":[{"value":"insp.opt.normal","guide":"..."}],
 *            "corners":[{"id":"...","label":"..."}]},
 *           {"id":"...","type":"wheel-data","unit":"PSI","dataType":"number","placeholder":"0"}
 *         ]
 *       }]
 *     }]
 *   }
 * }
 */

/** A single selectable option for "select" and "corner-select" items. */
data class InspectionOption(
    /** Localized display value, e.g. "Normal" once resolved through localeStrings. */
    val value: String,
    /** Optional per-option tooltip/hover text (localized), e.g. describes what "Monitor" means for this item. */
    val guide: String? = null,
    /**
     * Pre-resolution key, e.g. "insp.opt.normal", captured before locale
     * resolution. Column/color/short-label matching should use this instead
     * of [value] — it's stable across locales, whereas [value] becomes
     * translated display text once resolved and can't be reliably matched
     * against hardcoded English literals in every language.
     */
    val rawValue: String = value,
)

/** One measurable corner in a "corner-select" item (e.g. LF/RF/LR/RR tire positions). */
data class InspectionCorner(
    val id: String,
    val label: String,
)

/**
 * A single inspection question. `type` drives which layout renders it:
 * - "select": full-width question + Normal/Monitor/Needs Repair/N-A buttons below
 * - "corner-select": same as select, but one selectable row per corner (e.g. per wheel)
 * - "wheel-data" / "measurement" / "number" / "number-input": numeric input(s) with optional unit
 * - anything else: falls back to a free-text field
 */
data class InspectionItem(
    val id: String,
    val text: String,
    val type: String,
    val options: List<InspectionOption> = emptyList(),
    val corners: List<InspectionCorner> = emptyList(),
    val note: String? = null,
    val unit: String? = null,
    val dataType: String? = null,
    val placeholder: String? = null,
    val requiresPhoto: Boolean = false,
)

data class InspectionSection(
    val id: String,
    val title: String,
    val items: List<InspectionItem>,
)

data class InspectionGroup(
    val id: String,
    val title: String,
    val sections: List<InspectionSection>,
)

data class InspectionChecklist(
    val title: String,
    val description: String? = null,
    val groups: List<InspectionGroup>,
) {
    /** Flattens every item across every group/section — useful for scoring and answer lookups. */
    fun allItems(): List<InspectionItem> = groups.flatMap { g -> g.sections.flatMap { it.items } }
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Parses the nested `inspectionChecklist` JSON shape into [InspectionChecklist].
 * Returns null if the JSON doesn't contain a `groups` array (i.e. it's the older
 * flat field-list format, which callers should fall back to [parseInspectionFields] for).
 *
 * If [localeStrings] is provided, every localization-key field (title/text/note/guide/
 * option value/corner label) is resolved through it — mirrors iOS's LocaleManager.resolveJSON().
 */
fun parseInspectionChecklist(json: String?, localeStrings: Map<String, String> = emptyMap()): InspectionChecklist? {
    if (json.isNullOrBlank()) return null
    return try {
        val root = lenientJson.parseToJsonElement(json).jsonObject
        val checklistObj = root["inspectionChecklist"]?.jsonObject ?: root
        val groupsArr = checklistObj["groups"]?.jsonArray ?: return null

        fun resolve(key: String?): String? {
            if (key == null) return null
            return localeStrings[key] ?: key
        }

        fun parseOption(el: kotlinx.serialization.json.JsonElement): InspectionOption {
            return if (el is JsonObject) {
                val raw = el["value"]?.jsonPrimitive?.content ?: ""
                InspectionOption(
                    value = resolve(raw) ?: "",
                    guide = resolve(el["guide"]?.jsonPrimitive?.content),
                    rawValue = raw,
                )
            } else {
                val raw = el.jsonPrimitive.content
                InspectionOption(value = resolve(raw) ?: "", rawValue = raw)
            }
        }

        fun parseItem(obj: JsonObject): InspectionItem {
            val options = (obj["options"] as? JsonArray)?.map { parseOption(it) } ?: emptyList()
            val corners = (obj["corners"] as? JsonArray)?.mapNotNull { cEl ->
                val c = cEl as? JsonObject ?: return@mapNotNull null
                val id = c["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                InspectionCorner(id = id, label = resolve(c["label"]?.jsonPrimitive?.content) ?: id)
            } ?: emptyList()
            return InspectionItem(
                id = obj["id"]?.jsonPrimitive?.content ?: "",
                text = resolve(obj["text"]?.jsonPrimitive?.content) ?: "",
                type = obj["type"]?.jsonPrimitive?.content ?: "select",
                options = options,
                corners = corners,
                note = resolve(obj["note"]?.jsonPrimitive?.content),
                unit = obj["unit"]?.jsonPrimitive?.content,
                dataType = obj["dataType"]?.jsonPrimitive?.content,
                placeholder = obj["placeholder"]?.jsonPrimitive?.content,
                requiresPhoto = obj["requiresPhoto"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            )
        }

        fun parseSection(obj: JsonObject): InspectionSection {
            val id = obj["id"]?.jsonPrimitive?.content ?: ""
            val items = (obj["items"] as? JsonArray)?.mapNotNull { itEl ->
                (itEl as? JsonObject)?.let { parseItem(it) }
            } ?: emptyList()
            return InspectionSection(id = id, title = resolve(obj["title"]?.jsonPrimitive?.content) ?: id, items = items)
        }

        fun parseGroup(obj: JsonObject): InspectionGroup {
            val id = obj["id"]?.jsonPrimitive?.content ?: ""
            val sections = (obj["sections"] as? JsonArray)?.mapNotNull { sEl ->
                (sEl as? JsonObject)?.let { parseSection(it) }
            } ?: emptyList()
            return InspectionGroup(id = id, title = resolve(obj["title"]?.jsonPrimitive?.content) ?: id, sections = sections)
        }

        val groups = groupsArr.mapNotNull { (it as? JsonObject)?.let { g -> parseGroup(g) } }
        InspectionChecklist(
            title = resolve(checklistObj["title"]?.jsonPrimitive?.content) ?: "",
            description = resolve(checklistObj["description"]?.jsonPrimitive?.content),
            groups = groups,
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Parses a `type='Locale', subtype='Inspection'` config value into a flat
 * `insp.*` key -> translated string map. Unlike the `ItemAttributes` locale
 * config (which nests under a `"strings"` key), the Inspection locale config
 * is a flat top-level JSON object of key -> string pairs.
 */
fun parseInspectionLocaleStrings(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        lenientJson.parseToJsonElement(json).jsonObject
            .mapNotNull { (k, v) -> v.jsonPrimitive.content.let { k to it } }
            .toMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

/** Semantic column index for fixed cross-row alignment — mirrors iOS's avOptionColumn(_:). */
fun inspectionOptionColumn(value: String): Int = when (value) {
    "Normal", "insp.opt.normal" -> 0
    "Monitor", "insp.opt.monitor" -> 1
    "Needs Repair", "insp.opt.needs_repair" -> 2
    "N/A", "insp.opt.na" -> 3
    else -> -1
}

/** Short button label — mirrors iOS's avOptionLabel(_:). */
fun inspectionOptionShortLabel(value: String): String = when (value) {
    "Normal", "insp.opt.normal" -> "N"
    "Monitor", "insp.opt.monitor" -> "M"
    "Needs Repair", "insp.opt.needs_repair" -> "NR"
    "N/A", "insp.opt.na" -> "N/A"
    else -> if (value.length >= 2) value.take(2) else value.ifEmpty { "?" }
}

/** True when this item's answer options are tier-based (Normal/Monitor/Needs Repair) and should count toward the score bar. */
fun InspectionItem.isScoreEligible(): Boolean =
    (type == "select" || type == "corner-select") &&
        options.any { inspectionOptionColumn(it.rawValue) in 0..2 }
