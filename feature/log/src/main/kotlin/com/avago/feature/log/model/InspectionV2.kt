package com.avago.feature.log.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure (Android-free) logic for the cross-platform inspection "v2" data envelope.
 * Kotlin port of avagoweb's `inspectionData.ts`, `inspectionProgress.ts` and `openDefects.ts`.
 *
 * Envelope shape (stored in LogEntity.data):
 * `{ envelopeVersion: 2, subtype, mode, configSnapshot, values: {...}, signOff, result,
 *    outOfService, durationSec, defects: [...], resolvedDefects: [...], certification }`.
 *
 * Rule #1: never drop keys we don't understand — [buildEnvelope] always starts from the
 * stored JSON object and only overwrites the keys this client owns.
 */
object InspectionV2 {
    const val ENVELOPE_VERSION = 2

    const val NOTE_SUFFIX = "__note"
    const val SEVERITY_SUFFIX = "__sev"
    const val LOCATION_SUFFIX = "__loc"

    const val SEV_MINOR = "minor"
    const val SEV_MAJOR = "major"
    const val SEV_OUT_OF_SERVICE = "out_of_service"
    val SEVERITIES = listOf(SEV_MINOR, SEV_MAJOR, SEV_OUT_OF_SERVICE)

    val LOCATIONS = listOf(
        "fl", "fr", "rl", "rr", "front", "rear", "left", "right", "cab", "bed", "roof", "under", "interior", "engine",
    )

    const val RESULT_PASS = "pass"
    const val RESULT_PASS_WITH_DEFECTS = "pass_with_defects"
    const val RESULT_FAIL = "fail"

    const val RESOLUTION_REPAIRED = "repaired"
    const val RESOLUTION_STILL_PRESENT = "still_present"

    private val WHEEL_CORNERS = listOf("fl", "fr", "rl", "rr")

    /** Top-level keys owned by the envelope (everything else is preserved verbatim). */
    private val ENVELOPE_KEYS = setOf(
        "values", "subtype", "mode", "configSnapshot", "signOff", "result", "outOfService", "durationSec",
        "defects", "resolvedDefects", "certification", "envelopeVersion",
    )

    /** Legacy Android top-level meta keys (pre-v2 Android wrote answers flat at the top level). */
    private val LEGACY_META_KEYS = setOf("log_type", "inspection_subtype", "inspection_mode")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    enum class Bucket { NR, M, N, NA }

    // ------------------------------------------------------------------
    // Answers / meta keys
    // ------------------------------------------------------------------

    fun scoreBucket(value: String?): Bucket? = when (value) {
        "Needs Repair", "insp.opt.needs_repair" -> Bucket.NR
        "Monitor", "insp.opt.monitor" -> Bucket.M
        "Normal", "insp.opt.normal" -> Bucket.N
        "N/A", "insp.opt.na" -> Bucket.NA
        else -> null
    }

    fun isDefectBucket(bucket: Bucket?): Boolean = bucket == Bucket.NR || bucket == Bucket.M

    fun noteKey(itemId: String) = "$itemId$NOTE_SUFFIX"
    fun sevKey(itemId: String) = "$itemId$SEVERITY_SUFFIX"
    fun locKey(itemId: String) = "$itemId$LOCATION_SUFFIX"

    fun isMetaKey(key: String): Boolean =
        key.endsWith(NOTE_SUFFIX) || key.endsWith(SEVERITY_SUFFIX) || key.endsWith(LOCATION_SUFFIX)

    fun defaultSeverity(bucket: Bucket): String = if (bucket == Bucket.NR) SEV_MAJOR else SEV_MINOR

    fun severityOf(values: Map<String, String>, itemId: String, bucket: Bucket): String =
        values[sevKey(itemId)]?.takeIf { it in SEVERITIES } ?: defaultSeverity(bucket)

    fun locationOf(values: Map<String, String>, itemId: String): String? =
        values[locKey(itemId)]?.takeIf { it in LOCATIONS }

    /** Answer key for one corner of a corner-select item (contract: the corner id). */
    fun cornerKey(corner: InspectionCorner): String = corner.id

    /** Pre-v2 Android corner key, read for back-compat only. */
    fun legacyCornerKey(item: InspectionItem, corner: InspectionCorner): String = "${item.id}.${corner.id}"

    /** Stored answer for an item (or one of its corners), honouring the legacy corner key. */
    fun answerOf(values: Map<String, String>, item: InspectionItem, corner: InspectionCorner? = null): String? {
        val v = if (corner == null) values[item.id]
        else values[cornerKey(corner)]?.takeIf { it.isNotEmpty() } ?: values[legacyCornerKey(item, corner)]
        return v?.takeIf { it.isNotEmpty() }
    }

    /**
     * Maps a stored answer to the option's raw value. Older Android builds stored the localized
     * display text (e.g. "Reparatur nötig"); newer writes store the raw value ("insp.opt.needs_repair").
     */
    fun canonicalAnswer(item: InspectionItem, value: String?): String? {
        if (value.isNullOrEmpty()) return null
        if (scoreBucket(value) != null) return value
        return item.options.firstOrNull { it.value == value }?.rawValue ?: value
    }

    fun bucketOf(item: InspectionItem, value: String?): Bucket? = scoreBucket(canonicalAnswer(item, value))

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    data class Progress(val answered: Int, val total: Int, val nr: Int, val m: Int, val n: Int)

    fun computeProgress(checklist: InspectionChecklist?, values: Map<String, String>): Progress {
        var answered = 0; var total = 0; var nr = 0; var m = 0; var n = 0
        fun count(item: InspectionItem, corner: InspectionCorner?) {
            total++
            val v = answerOf(values, item, corner) ?: return
            answered++
            when (bucketOf(item, v)) {
                Bucket.NR -> nr++
                Bucket.M -> m++
                Bucket.N -> n++
                else -> {}
            }
        }
        checklist?.allItems()?.forEach { item ->
            when {
                item.type == "corner-select" -> item.corners.forEach { count(item, it) }
                item.type == "select" && item.options.isNotEmpty() -> count(item, null)
            }
        }
        return Progress(answered, total, nr, m, n)
    }

    // ------------------------------------------------------------------
    // Defects
    // ------------------------------------------------------------------

    data class EnvelopeDefect(
        val itemId: String,
        val key: String,
        val label: String,
        val cornerLabel: String? = null,
        val sectionTitle: String? = null,
        val answer: String,
        val bucket: Bucket,
        val severity: String,
        val note: String? = null,
        val location: String? = null,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("itemId", itemId)
            put("key", key)
            put("label", label)
            put("cornerLabel", cornerLabel)
            put("sectionTitle", sectionTitle)
            put("answer", answer)
            put("bucket", bucket.name)
            put("severity", severity)
            put("note", note)
            put("location", location)
        }
    }

    private fun rank(severity: String, bucket: Bucket): Int =
        if (severity == SEV_OUT_OF_SERVICE) 0 else if (bucket == Bucket.NR) 1 else 2

    /**
     * Monitor / Needs Repair answers in form order, out-of-service first, then NR, then M (stable).
     * Labels/section titles are the raw (possibly `insp.*`) config strings, like web.
     */
    fun collectDefects(checklist: InspectionChecklist?, values: Map<String, String>): List<EnvelopeDefect> {
        val out = mutableListOf<EnvelopeDefect>()
        checklist?.groups?.forEach { g ->
            g.sections.forEach { s ->
                s.items.forEach { item ->
                    val note = values[noteKey(item.id)]?.takeIf { it.isNotBlank() }
                    fun push(raw: String?, corner: InspectionCorner?) {
                        val answer = canonicalAnswer(item, raw) ?: return
                        val b = scoreBucket(answer)
                        if (b != Bucket.NR && b != Bucket.M) return
                        out += EnvelopeDefect(
                            itemId = item.id,
                            key = corner?.let { cornerKey(it) } ?: item.id,
                            label = item.rawText.ifEmpty { item.id },
                            cornerLabel = corner?.rawLabel,
                            sectionTitle = s.rawTitle,
                            answer = answer,
                            bucket = b,
                            severity = severityOf(values, item.id, b),
                            note = note,
                            location = locationOf(values, item.id),
                        )
                    }
                    when (item.type) {
                        "corner-select" -> item.corners.forEach { c -> push(answerOf(values, item, c), c) }
                        "select" -> push(answerOf(values, item), null)
                    }
                }
            }
        }
        return out.sortedBy { rank(it.severity, it.bucket) }
    }

    // ------------------------------------------------------------------
    // Ranges / result
    // ------------------------------------------------------------------

    data class RangeIssue(val itemId: String, val key: String, val corner: String?, val status: String)

    fun rangeStatus(raw: String, min: Double?, max: Double?): String? {
        if (raw.isBlank()) return null
        val n = raw.trim().toDoubleOrNull() ?: return null
        if (!n.isFinite()) return null
        if (min != null && n < min) return "low"
        if (max != null && n > max) return "high"
        return null
    }

    fun wheelDict(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            (json.parseToJsonElement(raw) as? JsonObject)
                ?.mapNotNull { (k, v) -> v.textOrNull()?.let { k to it } }?.toMap() ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun outOfRange(checklist: InspectionChecklist?, values: Map<String, String>): List<RangeIssue> {
        val out = mutableListOf<RangeIssue>()
        checklist?.allItems()?.forEach { item ->
            when (item.type) {
                "measurement" -> rangeStatus(values[item.id].orEmpty(), item.min, item.max)
                    ?.let { out += RangeIssue(item.id, item.id, null, it) }
                "number-input" -> item.inputs.forEach { input ->
                    rangeStatus(values[input.id].orEmpty(), input.min ?: item.min, input.max ?: item.max)
                        ?.let { out += RangeIssue(item.id, input.id, null, it) }
                }
                "wheel-data" -> {
                    val dict = wheelDict(values[item.id])
                    WHEEL_CORNERS.forEach { corner ->
                        val r = item.cornerRanges[corner]
                        rangeStatus(dict[corner].orEmpty(), r?.first ?: item.min, r?.second ?: item.max)
                            ?.let { out += RangeIssue(item.id, item.id, corner, it) }
                    }
                }
            }
        }
        return out
    }

    data class ResultSummary(val result: String, val outOfService: Boolean, val nr: Int, val m: Int, val ranges: Int)

    /** Fail on any Needs Repair or out-of-service defect; pass with defects on Monitor or out-of-range values. */
    fun computeResult(checklist: InspectionChecklist?, values: Map<String, String>): ResultSummary {
        val defects = collectDefects(checklist, values)
        val ranges = outOfRange(checklist, values).size
        val nr = defects.count { it.bucket == Bucket.NR }
        val m = defects.size - nr
        val oos = defects.any { it.severity == SEV_OUT_OF_SERVICE }
        val result = when {
            nr > 0 || oos -> RESULT_FAIL
            m > 0 || ranges > 0 -> RESULT_PASS_WITH_DEFECTS
            else -> RESULT_PASS
        }
        return ResultSummary(result, oos, nr, m, ranges)
    }

    // ------------------------------------------------------------------
    // Envelope parse / build
    // ------------------------------------------------------------------

    data class ResolvedDefect(
        val sourceLogId: String?,
        val itemId: String,
        val key: String,
        val resolution: String,
        val note: String? = null,
        val at: String,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("sourceLogId", sourceLogId)
            put("itemId", itemId)
            put("key", key)
            put("resolution", resolution)
            put("note", note)
            put("at", at)
        }
    }

    data class SignOff(val name: String, val signature: String?, val typed: String?, val signedAt: String)

    data class Certification(val status: String, val name: String, val signedAt: String, val note: String?)

    data class ParsedEnvelope(
        /** Every values entry as a string (non-string JSON is kept as its JSON text). */
        val values: Map<String, String> = emptyMap(),
        val subtype: String? = null,
        val mode: String? = null,
        val configSnapshot: JsonObject? = null,
        val signOff: SignOff? = null,
        val result: String? = null,
        val outOfService: Boolean = false,
        val durationSec: Double? = null,
        val defects: List<EnvelopeDefect> = emptyList(),
        /** True when the envelope carries a `defects` array (v2 writers). */
        val hasDefectsArray: Boolean = false,
        val resolvedDefects: List<ResolvedDefect> = emptyList(),
        val certification: Certification? = null,
        val envelopeVersion: Int? = null,
        /** True for the pre-v2 Android shape that stored answers flat at the top level. */
        val legacyFlat: Boolean = false,
    )

    fun parseDataObject(text: String?): JsonObject? {
        if (text.isNullOrBlank()) return null
        return try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun parseEnvelope(data: JsonObject?): ParsedEnvelope {
        if (data == null) return ParsedEnvelope()
        val valuesObj = data["values"] as? JsonObject
        val values: Map<String, String>
        val legacy: Boolean
        if (valuesObj != null) {
            values = valuesObj.mapNotNull { (k, v) -> v.valueText()?.let { k to it } }.toMap()
            legacy = false
        } else {
            values = data.filterKeys { it !in ENVELOPE_KEYS && it !in LEGACY_META_KEYS }
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { k to it.content } }
                .toMap()
            legacy = true
        }
        val defectsArr = data["defects"] as? JsonArray
        val defects = defectsArr?.mapNotNull { (it as? JsonObject)?.let(::parseEnvelopeDefect) } ?: emptyList()
        val resolved = (data["resolvedDefects"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val itemId = o["itemId"].textOrNull() ?: return@mapNotNull null
            ResolvedDefect(
                sourceLogId = o["sourceLogId"].textOrNull(),
                itemId = itemId,
                key = o["key"].textOrNull() ?: itemId,
                resolution = o["resolution"].textOrNull() ?: RESOLUTION_STILL_PRESENT,
                note = o["note"].textOrNull(),
                at = o["at"].textOrNull().orEmpty(),
            )
        } ?: emptyList()
        val signOff = (data["signOff"] as? JsonObject)?.let { so ->
            val name = so["name"].textOrNull() ?: return@let null
            SignOff(name, so["signature"].textOrNull(), so["typed"].textOrNull(), so["signedAt"].textOrNull().orEmpty())
        }
        val certification = (data["certification"] as? JsonObject)?.let { c ->
            val status = c["status"].textOrNull()?.takeIf { it == "corrected" || it == "not_needed" } ?: return@let null
            Certification(status, c["name"].textOrNull().orEmpty(), c["signedAt"].textOrNull().orEmpty(), c["note"].textOrNull())
        }
        return ParsedEnvelope(
            values = values,
            subtype = data["subtype"].textOrNull() ?: data["inspection_subtype"].textOrNull(),
            mode = data["mode"].textOrNull() ?: data["inspection_mode"].textOrNull(),
            configSnapshot = data["configSnapshot"] as? JsonObject,
            signOff = signOff,
            result = data["result"].textOrNull()?.takeIf { it == RESULT_PASS || it == RESULT_PASS_WITH_DEFECTS || it == RESULT_FAIL },
            outOfService = (data["outOfService"] as? JsonPrimitive)?.content == "true",
            durationSec = (data["durationSec"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
            defects = defects,
            hasDefectsArray = defectsArr != null,
            resolvedDefects = resolved,
            certification = certification,
            envelopeVersion = (data["envelopeVersion"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt(),
            legacyFlat = legacy,
        )
    }

    private fun parseEnvelopeDefect(o: JsonObject): EnvelopeDefect? {
        val itemId = o["itemId"].textOrNull() ?: return null
        val bucket = if (o["bucket"].textOrNull() == "M") Bucket.M else Bucket.NR
        return EnvelopeDefect(
            itemId = itemId,
            key = o["key"].textOrNull() ?: itemId,
            label = o["label"].textOrNull() ?: itemId,
            cornerLabel = o["cornerLabel"].textOrNull(),
            sectionTitle = o["sectionTitle"].textOrNull(),
            answer = o["answer"].textOrNull().orEmpty(),
            bucket = bucket,
            severity = o["severity"].textOrNull()?.takeIf { it in SEVERITIES } ?: defaultSeverity(bucket),
            note = o["note"].textOrNull(),
            location = o["location"].textOrNull(),
        )
    }

    /**
     * Writes the v2 envelope. Starts from [stored] (the previously saved `data` object) and only
     * overwrites keys this client owns, so keys written by web/iOS/newer clients survive an edit.
     *
     * [values] is the complete answer map as edited in the form (it was seeded from the stored
     * values, so a stored key missing here was removed on purpose). Entries whose text is unchanged
     * keep their original JSON element (numbers / objects stay as-is).
     *
     * [newResolved] entries replace stored `resolvedDefects` entries with the same key; other stored
     * entries are kept verbatim.
     */
    fun buildEnvelope(
        stored: JsonObject?,
        values: Map<String, String>,
        subtype: String?,
        mode: String?,
        configSnapshot: JsonObject?,
        defects: List<EnvelopeDefect>,
        result: String?,
        outOfService: Boolean,
        newResolved: List<ResolvedDefect> = emptyList(),
        logType: String = "inspection",
    ): JsonObject {
        val base = LinkedHashMap<String, JsonElement>(stored ?: emptyMap())
        val parsed = parseEnvelope(stored)
        val storedValues: Map<String, JsonElement> = (stored?.get("values") as? JsonObject)
            ?: if (stored != null && parsed.legacyFlat) parsed.values.keys.associateWith { stored.getValue(it) } else emptyMap()
        if (parsed.legacyFlat) parsed.values.keys.forEach { base.remove(it) }

        val mergedValues = LinkedHashMap<String, JsonElement>()
        storedValues.forEach { (k, el) ->
            val v = values[k] ?: return@forEach
            mergedValues[k] = if (el.valueText() == v) el else JsonPrimitive(v)
        }
        values.forEach { (k, v) -> if (k !in mergedValues) mergedValues[k] = JsonPrimitive(v) }

        base["values"] = JsonObject(mergedValues)
        base["envelopeVersion"] = JsonPrimitive(ENVELOPE_VERSION)
        base["log_type"] = JsonPrimitive(logType)
        if (!subtype.isNullOrBlank()) {
            base["subtype"] = JsonPrimitive(subtype)
            base["inspection_subtype"] = JsonPrimitive(subtype)
        }
        if (!mode.isNullOrBlank()) {
            base["mode"] = JsonPrimitive(mode)
            base["inspection_mode"] = JsonPrimitive(mode)
        }
        if (configSnapshot != null) base["configSnapshot"] = configSnapshot
        base["defects"] = JsonArray(defects.map { it.toJson() })
        if (result != null) base["result"] = JsonPrimitive(result)
        if (outOfService) base["outOfService"] = JsonPrimitive(true) else base.remove("outOfService")

        if (newResolved.isNotEmpty()) {
            val replaced = newResolved.map { it.key }.toSet()
            val kept = (stored?.get("resolvedDefects") as? JsonArray)?.filter { el ->
                (el as? JsonObject)?.get("key").textOrNull() !in replaced
            } ?: emptyList()
            base["resolvedDefects"] = JsonArray(kept + newResolved.map { it.toJson() })
        }
        return JsonObject(base)
    }

    /** Adds `inspectionChecklist` (raw config JSON) to a snapshot, keeping any extra snapshot keys. */
    fun buildConfigSnapshot(rawConfig: String?, previous: JsonObject? = null, extra: Map<String, JsonElement> = emptyMap()): JsonObject? {
        val cfg = parseDataObject(rawConfig)
        val checklist = (cfg?.get("inspectionChecklist") as? JsonObject) ?: cfg?.takeIf { it["groups"] is JsonArray }
        if (checklist == null && extra.isEmpty()) return previous
        val out = LinkedHashMap<String, JsonElement>(previous ?: emptyMap())
        if (checklist != null) out["inspectionChecklist"] = checklist
        out.putAll(extra)
        return JsonObject(out)
    }

    // ------------------------------------------------------------------
    // Open-defect register (client-side derivation)
    // ------------------------------------------------------------------

    data class OpenDefect(
        val id: String,
        val assetId: String?,
        val itemId: String,
        val key: String,
        val label: String,
        val cornerLabel: String?,
        val sectionTitle: String?,
        val bucket: Bucket,
        val severity: String,
        val note: String?,
        val location: String?,
        val sourceLogId: String?,
        val reportedAt: String,
        val lastSeenLogId: String?,
        val lastSeenAt: String,
        val status: String,
    )

    data class HistoryEntry(
        val logId: String?,
        val assetId: String?,
        /** ISO-8601 date/time used for ordering. */
        val logDate: String,
        val category: String?,
        val data: JsonObject?,
    )

    /** Defects recorded on one saved entry: the envelope list (v2), else recomputed from its snapshot. */
    fun entryDefects(data: JsonObject?): List<EnvelopeDefect> {
        val parsed = parseEnvelope(data)
        if (parsed.hasDefectsArray) return parsed.defects
        val checklist = parsed.configSnapshot?.let { parseInspectionChecklist(it.toString()) }
        if (checklist != null && checklist.groups.isNotEmpty()) return collectDefects(checklist, parsed.values)
        return parsed.values.mapNotNull { (key, v) ->
            val b = scoreBucket(v)
            if ((b != Bucket.NR && b != Bucket.M) || key.contains("__")) return@mapNotNull null
            EnvelopeDefect(itemId = key, key = key, label = key, answer = v, bucket = b, severity = defaultSeverity(b))
        }
    }

    /** Replays inspection history oldest → newest; returns the defects still open, most severe first. */
    fun deriveOpenDefects(entries: List<HistoryEntry>, excludeLogId: String? = null): List<OpenDefect> {
        val inspections = entries
            .filter { e ->
                (excludeLogId == null || e.logId != excludeLogId) &&
                    e.category?.contains("inspect", ignoreCase = true) == true && e.data != null
            }
            .sortedBy { it.logDate }
        val open = LinkedHashMap<String, OpenDefect>()
        for (e in inspections) {
            val parsed = parseEnvelope(e.data)
            val logId = e.logId
            for ((key, v) in parsed.values) {
                if (key.contains("__")) continue
                val b = scoreBucket(v)
                if (b == Bucket.N || b == Bucket.NA) open.remove(key)
            }
            parsed.resolvedDefects.filter { it.resolution == RESOLUTION_REPAIRED }.forEach { open.remove(it.key) }
            for (d in entryDefects(e.data)) {
                val prev = open[d.key]
                open[d.key] = OpenDefect(
                    id = prev?.id ?: "${logId ?: "local"}:${d.key}",
                    assetId = e.assetId,
                    itemId = d.itemId,
                    key = d.key,
                    label = d.label,
                    cornerLabel = d.cornerLabel,
                    sectionTitle = d.sectionTitle,
                    bucket = d.bucket,
                    severity = d.severity,
                    note = d.note ?: prev?.note,
                    location = d.location ?: prev?.location,
                    sourceLogId = prev?.sourceLogId ?: logId,
                    reportedAt = prev?.reportedAt ?: e.logDate,
                    lastSeenLogId = logId,
                    lastSeenAt = e.logDate,
                    status = if (d.bucket == Bucket.M) "monitoring" else "open",
                )
            }
        }
        return sortOpen(open.values.toList())
    }

    fun sortOpen(list: List<OpenDefect>): List<OpenDefect> =
        list.sortedWith(compareBy<OpenDefect> { rank(it.severity, it.bucket) }.thenByDescending { it.lastSeenAt })

    /** Server register row (AvagoSvc `inspection_defects`) → [OpenDefect]. */
    fun fromServerDefect(row: JsonObject): OpenDefect {
        fun s(k: String) = row[k].textOrNull()
        val bucket = if (s("bucket") == "M") Bucket.M else Bucket.NR
        val sev = s("severity")?.takeIf { it == SEV_MINOR || it == SEV_OUT_OF_SERVICE } ?: SEV_MAJOR
        val status = s("status")?.takeIf { it in setOf("open", "monitoring", "repaired", "deferred") } ?: "open"
        return OpenDefect(
            id = s("defect_id") ?: "${s("source_log_id")}:${s("item_key")}",
            assetId = s("asset_id"),
            itemId = s("item_id").orEmpty(),
            key = s("item_key") ?: s("item_id").orEmpty(),
            label = s("label").orEmpty(),
            cornerLabel = s("corner_label"),
            sectionTitle = s("section_title"),
            bucket = bucket,
            severity = sev,
            note = s("note"),
            location = s("location"),
            sourceLogId = s("source_log_id"),
            reportedAt = s("reported_at").orEmpty(),
            lastSeenLogId = s("last_seen_log_id"),
            lastSeenAt = s("last_seen_at") ?: s("reported_at").orEmpty(),
            status = status,
        )
    }

    /** `resolvedDefects` entry for a carried-forward defect. */
    fun resolvedEntry(defect: OpenDefect, resolution: String, at: String): ResolvedDefect =
        ResolvedDefect(
            sourceLogId = defect.sourceLogId,
            itemId = defect.itemId,
            key = defect.key,
            resolution = resolution,
            note = null,
            at = at,
        )

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    /** String content of a non-null primitive, else null. */
    internal fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    /** Values-dict text: primitives as their content, objects/arrays as JSON text, null → null. */
    private fun JsonElement.valueText(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> toString()
    }
}
