package com.avago.feature.log.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Pure (Android-free) inspection validation and field helpers.
 * Kotlin port of avagoweb `inspectionProgress.ts` (measure slots, range severity, required /
 * note targets), `meterSanity.ts`, `inspectionOptions.ts` (quick phrases) and `VehicleSketch.tsx`.
 */
object InspectionValidation {

    val WHEEL_CORNERS = listOf("fl", "fr", "rl", "rr")

    // ------------------------------------------------------------------
    // Measurements
    // ------------------------------------------------------------------

    /** One numeric input. Wheel-data corners share the item key (a JSON dict) and carry [corner]. */
    data class MeasureSlot(
        val key: String,
        val corner: String? = null,
        val label: String? = null,
        val min: Double? = null,
        val max: Double? = null,
    )

    fun isNumericType(type: String): Boolean = type == "measurement" || type == "number-input" || type == "wheel-data"

    fun measureSlots(item: InspectionItem): List<MeasureSlot> = when (item.type) {
        "measurement" -> listOf(MeasureSlot(item.id, min = item.min, max = item.max))
        "number-input" -> item.inputs.map { MeasureSlot(it.id, label = it.label, min = it.min ?: item.min, max = it.max ?: item.max) }
        "wheel-data" -> WHEEL_CORNERS.map { c ->
            val r = item.cornerRanges[c]
            MeasureSlot(item.id, corner = c, label = c, min = r?.first ?: item.min, max = r?.second ?: item.max)
        }
        else -> emptyList()
    }

    fun measureValue(values: Map<String, String>, slot: MeasureSlot): String =
        if (slot.corner != null) InspectionV2.wheelDict(values[slot.key])[slot.corner].orEmpty()
        else values[slot.key].orEmpty()

    /** `minor`, or `major` when more than 25% beyond the violated bound; null when in range. */
    fun rangeSeverity(raw: String, min: Double?, max: Double?): String? {
        val status = InspectionV2.rangeStatus(raw, min, max) ?: return null
        val n = raw.trim().toDouble()
        val bound = if (status == "low") min!! else max!!
        val span = abs(bound).takeIf { it != 0.0 } ?: 1.0
        return if (abs(n - bound) / span > 0.25) InspectionV2.SEV_MAJOR else InspectionV2.SEV_MINOR
    }

    /** Worst range severity over all of an item's numeric slots. */
    fun itemRangeSeverity(item: InspectionItem, values: Map<String, String>): String? {
        var worst: String? = null
        for (slot in measureSlots(item)) {
            val s = rangeSeverity(measureValue(values, slot), slot.min, slot.max)
            if (s == InspectionV2.SEV_MAJOR) return s
            if (s != null) worst = InspectionV2.SEV_MINOR
        }
        return worst
    }

    /** Value for writing [raw] into [slot] (wheel corners are merged into the item's JSON dict). */
    fun slotWrite(values: Map<String, String>, slot: MeasureSlot, raw: String): Pair<String, String> {
        if (slot.corner == null) return slot.key to raw
        val dict = InspectionV2.wheelDict(values[slot.key]).toMutableMap()
        if (raw.isEmpty()) dict.remove(slot.corner) else dict[slot.corner] = raw
        val text = if (dict.isEmpty()) "" else JsonObject(dict.mapValues { JsonPrimitive(it.value) }).toString()
        return slot.key to text
    }

    /**
     * Web `writeMeasure`: applies [writes] and keeps `<id>__sev` in step with the worst out-of-range
     * reading. A manual out-of-service is never overridden. An empty value means "remove the key".
     */
    fun measureWrites(item: InspectionItem, values: Map<String, String>, writes: List<Pair<String, String>>): List<Pair<String, String>> {
        val next = values + writes
        val sev = itemRangeSeverity(item, next).orEmpty()
        val cur = values[InspectionV2.sevKey(item.id)].orEmpty()
        val out = writes.toMutableList()
        if (cur != InspectionV2.SEV_OUT_OF_SERVICE && sev != cur) out += InspectionV2.sevKey(item.id) to sev
        return out
    }

    // ------------------------------------------------------------------
    // Required answers / defect notes
    // ------------------------------------------------------------------

    data class FocusTarget(val key: String, val itemId: String, val sectionId: String)

    private fun isAnswered(values: Map<String, String>, key: String) = !values[key].isNullOrBlank()

    private inline fun forEachItem(checklist: InspectionChecklist?, block: (InspectionSection, InspectionItem) -> Unit) {
        checklist?.groups?.forEach { g -> g.sections.forEach { s -> s.items.forEach { block(s, it) } } }
    }

    /** Needs Repair answers and anything out of service (incl. out-of-range measurements) without a note. */
    fun missingDefectNotes(checklist: InspectionChecklist?, values: Map<String, String>): List<FocusTarget> {
        val out = mutableListOf<FocusTarget>()
        forEachItem(checklist) { s, item ->
            if (values[InspectionV2.noteKey(item.id)].orEmpty().isNotBlank()) return@forEachItem
            val oos = values[InspectionV2.sevKey(item.id)] == InspectionV2.SEV_OUT_OF_SERVICE
            val key: String? = when {
                item.type == "corner-select" -> {
                    val b = { c: InspectionCorner -> InspectionV2.bucketOf(item, InspectionV2.answerOf(values, item, c)) }
                    item.corners.firstOrNull { b(it) == InspectionV2.Bucket.NR }?.id
                        ?: if (oos) item.corners.firstOrNull { InspectionV2.isDefectBucket(b(it)) }?.id else null
                }
                item.type == "select" -> {
                    val b = InspectionV2.bucketOf(item, InspectionV2.answerOf(values, item))
                    if (b == InspectionV2.Bucket.NR || (oos && b == InspectionV2.Bucket.M)) item.id else null
                }
                oos && itemRangeSeverity(item, values) != null -> measureSlots(item).firstOrNull()?.key ?: item.id
                else -> null
            }
            if (key != null) out += FocusTarget(key, item.id, s.id)
        }
        return out
    }

    /** Required items that still lack an answer / measurement, in form order. */
    fun missingRequired(checklist: InspectionChecklist?, values: Map<String, String>): List<FocusTarget> {
        val out = mutableListOf<FocusTarget>()
        forEachItem(checklist) { s, item ->
            if (!item.required) return@forEachItem
            when (item.type) {
                "corner-select" -> item.corners.forEach { c ->
                    if (InspectionV2.answerOf(values, item, c) == null) out += FocusTarget(c.id, item.id, s.id)
                }
                "select" -> if (!isAnswered(values, item.id)) out += FocusTarget(item.id, item.id, s.id)
                "wheel-data" -> {
                    val d = InspectionV2.wheelDict(values[item.id])
                    if (WHEEL_CORNERS.any { d[it].isNullOrEmpty() }) out += FocusTarget(item.id, item.id, s.id)
                }
                else -> {
                    val slots = measureSlots(item)
                    if (slots.isEmpty()) {
                        if (!isAnswered(values, item.id)) out += FocusTarget(item.id, item.id, s.id)
                    } else {
                        slots.firstOrNull { measureValue(values, it).isEmpty() }
                            ?.let { out += FocusTarget(it.key, item.id, s.id) }
                    }
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Section progress
    // ------------------------------------------------------------------

    data class SectionProgress(val sectionId: String, val title: String, val answered: Int, val total: Int, val attention: Int)

    /** Per-section answered / total over score targets (select + corner-select), plus open attention items. */
    fun sectionProgress(checklist: InspectionChecklist?, values: Map<String, String>): List<SectionProgress> {
        val attention = (missingRequired(checklist, values) + missingDefectNotes(checklist, values))
            .groupingBy { it.sectionId }.eachCount()
        return checklist?.groups.orEmpty().flatMap { g ->
            g.sections.map { s ->
                var answered = 0
                var total = 0
                s.items.forEach { item ->
                    when {
                        item.type == "corner-select" -> item.corners.forEach { c ->
                            total++
                            if (InspectionV2.answerOf(values, item, c) != null) answered++
                        }
                        item.type == "select" && item.options.isNotEmpty() -> {
                            total++
                            if (InspectionV2.answerOf(values, item) != null) answered++
                        }
                    }
                }
                SectionProgress(s.id, s.title, answered, total, attention[s.id] ?: 0)
            }
        }
    }

    // ------------------------------------------------------------------
    // Meter sanity
    // ------------------------------------------------------------------

    const val METER_ODOMETER = "odometer"
    const val METER_ENGINE_HOURS = "engine_hours"
    private const val DAY_MS = 86_400_000L

    fun meterRateLimit(kind: String): Double = if (kind == METER_ENGINE_HOURS) 24.0 else 1500.0

    data class MeterPoint(val value: Double, val atMs: Long, val logId: String? = null)

    data class MeterIssue(val type: String, val entered: Double, val last: MeterPoint, val perDay: Long? = null, val limit: Double? = null) {
        companion object {
            const val LOWER = "lower"
            const val RATE = "rate"
        }
    }

    /** "12,345 mi" / "1 234.5" → number; null when not numeric. */
    fun parseMeterValue(raw: String?): Double? {
        if (raw == null) return null
        val cleaned = raw.replace(Regex("[\\s,]"), "").replace(Regex("[^\\d.+-]"), "")
        if (cleaned.isEmpty() || !cleaned.any { it.isDigit() }) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * Compares [entered] with the latest reading at or before [cutoffMs] (end of the entry's day).
     * `lower` when below it; `rate` when the increase per day (elapsed floored at one day) exceeds
     * the limit. Non-blocking: callers show a confirm.
     */
    fun checkMeterSanity(
        entered: Double?,
        enteredAtMs: Long,
        cutoffMs: Long,
        points: List<MeterPoint>,
        kind: String,
        excludeLogId: String? = null,
    ): MeterIssue? {
        if (entered == null || !entered.isFinite() || entered <= 0) return null
        var last: MeterPoint? = null
        for (p in points) {
            if (excludeLogId != null && p.logId == excludeLogId) continue
            if (p.value <= 0 || p.atMs > cutoffMs) continue
            if (last == null || p.atMs >= last.atMs) last = p
        }
        if (last == null) return null
        if (entered < last.value) return MeterIssue(MeterIssue.LOWER, entered, last)
        val days = max(1.0, (enteredAtMs - last.atMs).toDouble() / DAY_MS)
        val perDay = (entered - last.value) / days
        val limit = meterRateLimit(kind)
        return if (perDay > limit) MeterIssue(MeterIssue.RATE, entered, last, perDay.roundToLong(), limit) else null
    }

    // ------------------------------------------------------------------
    // Quick phrases (web `inspectionOptions.ts`)
    // ------------------------------------------------------------------

    val QUICK_PHRASES = listOf("low", "worn", "cracked", "leaking", "missing", "loose", "damaged", "dirty", "inoperative")

    /** Canonical comparison form: trimmed, lower-case, single spaces, no trailing punctuation. */
    fun normalizePhrase(p: String): String =
        p.trim().replace(Regex("\\s+"), " ").replace(Regex("[.;:!?]+$"), "").lowercase()

    /** Appends a phrase to a note, comma-separated, without duplicating it; capitalized when empty. */
    fun appendPhrase(note: String, phrase: String): String {
        val p = phrase.trim()
        if (p.isEmpty()) return note
        val base = note.trimEnd()
        if (base.isEmpty()) return p.replaceFirstChar { it.uppercase() }
        val parts = base.split(Regex("[,;]\\s*")).map(::normalizePhrase)
        if (normalizePhrase(p) in parts) return note
        val sep = if (base.last() in ".,;:!?") " " else ", "
        return base + sep + p.lowercase()
    }
}
