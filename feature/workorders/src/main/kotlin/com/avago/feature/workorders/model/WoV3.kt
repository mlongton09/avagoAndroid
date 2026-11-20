package com.avago.feature.workorders.model

import com.avago.core.data.domain.ReferenceValue
import com.avago.core.data.domain.SlaPolicy
import com.avago.core.data.domain.TimeEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Work-order v3 attribute helpers (contract §2). Mirrors web `src/lib/workOrders/v3.ts`.
 * All writes go through [mergeWoAttributes] so unknown attribute keys are preserved.
 */

const val WO_KEY_CLOSEOUT = "closeout"
const val WO_KEY_VENDOR = "vendor"
const val WO_KEY_SOURCE = "source"
const val WO_KEY_SLA = "sla"
const val WO_KEY_HOLD = "hold"

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseWoAttributes(raw: String?): JsonObject {
    if (raw.isNullOrBlank()) return JsonObject(emptyMap())
    return runCatching { lenientJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: JsonObject(emptyMap())
}

/**
 * Shallow-merges [patch] over [existing]; a [JsonNull] value clears the key.
 * Keys absent from [patch] are kept untouched.
 */
fun mergeWoAttributes(existing: JsonObject, patch: Map<String, JsonElement?>): JsonObject {
    val out = LinkedHashMap(existing)
    for ((k, v) in patch) {
        if (v == null || v is JsonNull) out.remove(k) else out[k] = v
    }
    return JsonObject(out)
}

fun mergeWoAttributesJson(existingRaw: String?, patch: Map<String, JsonElement?>): String =
    mergeWoAttributes(parseWoAttributes(existingRaw), patch).toString()

// ── JSON helpers ────────────────────────────────────────────────────────────

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.num(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

private fun MutableMap<String, JsonElement>.putIfNotNull(key: String, v: String?) {
    if (!v.isNullOrBlank()) put(key, JsonPrimitive(v))
}

private fun MutableMap<String, JsonElement>.putIfNotNull(key: String, v: Double?) {
    if (v != null) put(key, JsonPrimitive(v))
}

// ── Close-out ───────────────────────────────────────────────────────────────

data class WoSignoff(
    val name: String,
    val signedAt: String,
)

data class WoCloseout(
    val failureCode: String? = null,
    val causeCode: String? = null,
    val remedyCode: String? = null,
    val resolutionNotes: String? = null,
    val downtimeStart: String? = null,
    val downtimeEnd: String? = null,
    val meterReading: Double? = null,
    val meterUnit: String? = null,
    val partsReconciled: Boolean? = null,
    val pmResetPlanIds: List<String> = emptyList(),
    val signoff: WoSignoff? = null,
    val completedBy: String? = null,
    /** Keys inside `closeout` this client doesn't model; preserved on write. */
    val extra: JsonObject = JsonObject(emptyMap()),
) {
    fun toJson(): JsonObject {
        val m = LinkedHashMap<String, JsonElement>(extra)
        listOf(
            "failure_code", "cause_code", "remedy_code", "resolution_notes", "downtime_start",
            "downtime_end", "meter_reading", "meter_unit", "parts_reconciled", "pm_reset_plan_ids",
            "signoff", "completed_by",
        ).forEach { m.remove(it) }
        m.putIfNotNull("failure_code", failureCode)
        m.putIfNotNull("cause_code", causeCode)
        m.putIfNotNull("remedy_code", remedyCode)
        m.putIfNotNull("resolution_notes", resolutionNotes)
        m.putIfNotNull("downtime_start", downtimeStart)
        m.putIfNotNull("downtime_end", downtimeEnd)
        m.putIfNotNull("meter_reading", meterReading)
        m.putIfNotNull("meter_unit", meterUnit)
        partsReconciled?.let { m["parts_reconciled"] = JsonPrimitive(it) }
        if (pmResetPlanIds.isNotEmpty()) m["pm_reset_plan_ids"] = JsonArray(pmResetPlanIds.map { JsonPrimitive(it) })
        signoff?.let {
            val prev = (extra["signoff"] as? JsonObject) ?: JsonObject(emptyMap())
            m["signoff"] = JsonObject(prev + mapOf("name" to JsonPrimitive(it.name), "signed_at" to JsonPrimitive(it.signedAt)))
        }
        m.putIfNotNull("completed_by", completedBy)
        return JsonObject(m)
    }

    companion object {
        fun from(attrs: JsonObject): WoCloseout {
            val c = attrs.obj(WO_KEY_CLOSEOUT) ?: return WoCloseout()
            val s = c.obj("signoff")
            return WoCloseout(
                failureCode = c.str("failure_code"),
                causeCode = c.str("cause_code"),
                remedyCode = c.str("remedy_code"),
                resolutionNotes = c.str("resolution_notes"),
                downtimeStart = c.str("downtime_start"),
                downtimeEnd = c.str("downtime_end"),
                meterReading = c.num("meter_reading"),
                meterUnit = c.str("meter_unit"),
                partsReconciled = c.bool("parts_reconciled"),
                pmResetPlanIds = c.strList("pm_reset_plan_ids"),
                signoff = s?.str("name")?.let { WoSignoff(it, s.str("signed_at") ?: "") },
                completedBy = c.str("completed_by"),
                extra = c,
            )
        }
    }
}

/** round((end-start)/60000) when end >= start, else null. */
fun downtimeMinutes(startIso: String?, endIso: String?): Long? {
    val s = parseIsoMs(startIso) ?: return null
    val e = parseIsoMs(endIso) ?: return null
    if (e < s) return null
    return ((e - s) / 60000.0).roundToLong()
}

fun parseIsoMs(iso: String?): Long? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

data class WoCloseoutOptions(
    val requireSignature: Boolean = false,
    val requireDowntime: Boolean = false,
)

data class CloseoutValidation(val ok: Boolean, val missing: List<String>)

/** Minimal checklist view so validation stays pure/testable. */
data class ChecklistRowState(val required: Boolean, val completed: Boolean)

private val PROCEDURE_ROW_TYPES = setOf(
    "CHECKBOX", "TEXT", "NUMBER", "DATE", "SELECT", "HEADING", "FILE", "SIGNATURE", "CYCLE_COUNT", "METER", "STEP",
)
private val OPTIONAL_BY_DEFAULT_ROW_TYPES = setOf("TEXT", "DATE", "FILE")

/** Unknown / missing row types are treated as STEP (matches web `normalizeRowType`). */
fun normalizeRowType(rowType: String?): String =
    rowType?.trim()?.uppercase()?.takeIf { it in PROCEDURE_ROW_TYPES } ?: "STEP"

/**
 * Procedure rows have no server `required` column. HEADING is never required; otherwise an
 * explicit [explicitRequired] wins; else STEP/CHECKBOX/NUMBER/METER/CYCLE_COUNT/SELECT/SIGNATURE
 * are required and TEXT/DATE/FILE are optional.
 */
fun isChecklistRowRequired(rowType: String?, explicitRequired: Boolean? = null): Boolean {
    val t = normalizeRowType(rowType)
    if (t == "HEADING") return false
    explicitRequired?.let { return it }
    return t !in OPTIONAL_BY_DEFAULT_ROW_TYPES
}

/**
 * Completion gate (contract §2). Missing keys:
 * failure_code, cause_code, remedy_code, resolution_notes, meter_reading, checklist, downtime, signoff.
 */
fun validateCloseout(
    closeout: WoCloseout,
    hasAsset: Boolean,
    checklist: List<ChecklistRowState>,
    opts: WoCloseoutOptions = WoCloseoutOptions(),
): CloseoutValidation {
    val missing = mutableListOf<String>()
    if (closeout.failureCode.isNullOrBlank()) missing += "failure_code"
    if (closeout.causeCode.isNullOrBlank()) missing += "cause_code"
    if (closeout.remedyCode.isNullOrBlank()) missing += "remedy_code"
    if (closeout.resolutionNotes.isNullOrBlank()) missing += "resolution_notes"
    if (hasAsset && closeout.meterReading == null) missing += "meter_reading"
    if (checklist.any { it.required && !it.completed }) missing += "checklist"
    if (opts.requireDowntime && downtimeMinutes(closeout.downtimeStart, closeout.downtimeEnd) == null) missing += "downtime"
    if (opts.requireSignature && closeout.signoff?.name.isNullOrBlank()) missing += "signoff"
    return CloseoutValidation(missing.isEmpty(), missing)
}

/**
 * Reads close-out opt-ins from either the flat account-feature keys
 * (`wo_closeout_require_signature` / `wo_closeout_require_downtime`) or a nested
 * `wo_closeout.require_signature/require_downtime` config blob.
 */
fun parseCloseoutOptions(raw: String?): WoCloseoutOptions {
    val o = parseWoAttributes(raw)
    val c = o.obj("wo_closeout") ?: o
    return WoCloseoutOptions(
        requireSignature = (o.bool("wo_closeout_require_signature") ?: c.bool("require_signature")) == true,
        requireDowntime = (o.bool("wo_closeout_require_downtime") ?: c.bool("require_downtime")) == true,
    )
}

// ── Vendor / Hold / Source ─────────────────────────────────────────────────

data class WoVendor(
    val vendorId: String? = null,
    val vendorName: String? = null,
    val poId: String? = null,
    val invoiceRef: String? = null,
    val invoiceTotal: Double? = null,
    val estimateTotal: Double? = null,
    val extra: JsonObject = JsonObject(emptyMap()),
) {
    val isEmpty: Boolean
        get() = vendorId == null && vendorName == null && poId == null && invoiceRef == null &&
            invoiceTotal == null && estimateTotal == null

    fun toJson(): JsonObject {
        val m = LinkedHashMap<String, JsonElement>(extra)
        listOf("vendor_id", "vendor_name", "po_id", "invoice_ref", "invoice_total", "estimate_total").forEach { m.remove(it) }
        m.putIfNotNull("vendor_id", vendorId)
        m.putIfNotNull("vendor_name", vendorName)
        m.putIfNotNull("po_id", poId)
        m.putIfNotNull("invoice_ref", invoiceRef)
        m.putIfNotNull("invoice_total", invoiceTotal)
        m.putIfNotNull("estimate_total", estimateTotal)
        return JsonObject(m)
    }

    companion object {
        fun from(attrs: JsonObject): WoVendor {
            val v = attrs.obj(WO_KEY_VENDOR) ?: return WoVendor()
            return WoVendor(
                v.str("vendor_id"), v.str("vendor_name"), v.str("po_id"), v.str("invoice_ref"),
                v.num("invoice_total"), v.num("estimate_total"), v,
            )
        }
    }
}

val WO_HOLD_REASONS = listOf("parts", "vendor", "approval", "customer", "other")

data class WoHold(val reason: String, val since: String?, val note: String?) {
    fun toJson(): JsonObject {
        val m = LinkedHashMap<String, JsonElement>()
        m["reason"] = JsonPrimitive(reason)
        m.putIfNotNull("since", since)
        m.putIfNotNull("note", note)
        return JsonObject(m)
    }

    companion object {
        fun from(attrs: JsonObject): WoHold? {
            val h = attrs.obj(WO_KEY_HOLD) ?: return null
            val reason = h.str("reason") ?: return null
            return WoHold(reason, h.str("since"), h.str("note"))
        }
    }
}

fun woSourceJson(
    kind: String,
    defectIds: List<String> = emptyList(),
    inspectionLogId: String? = null,
    templateId: String? = null,
    pmPlanId: String? = null,
): JsonObject {
    val m = LinkedHashMap<String, JsonElement>()
    m["kind"] = JsonPrimitive(kind)
    if (defectIds.isNotEmpty()) m["defect_ids"] = JsonArray(defectIds.map { JsonPrimitive(it) })
    m.putIfNotNull("inspection_log_id", inspectionLogId)
    m.putIfNotNull("template_id", templateId)
    m.putIfNotNull("pm_plan_id", pmPlanId)
    return JsonObject(m)
}

// ── Codes ───────────────────────────────────────────────────────────────────

enum class WoCodeKind(val key: String) { FAILURE("failure"), CAUSE("cause"), REMEDY("remedy") }

data class WoCode(val codeId: String, val kind: WoCodeKind, val code: String, val label: String, val sortOrder: Int)

private fun defaults(kind: WoCodeKind, vararg pairs: Pair<String, String>) =
    pairs.mapIndexed { i, (c, l) -> WoCode("default:$c", kind, c, l, i) }

/** Copy of web `DEFAULT_WO_CODES` (src/lib/workOrders/defaults.ts). */
val DEFAULT_WO_CODES: Map<WoCodeKind, List<WoCode>> = mapOf(
    WoCodeKind.FAILURE to defaults(
        WoCodeKind.FAILURE,
        "F-WEAR" to "Worn", "F-LEAK" to "Leaking", "F-BRKN" to "Broken / cracked",
        "F-ELEC" to "Electrical fault", "F-NOOP" to "Inoperative", "F-NOIS" to "Noise / vibration",
        "F-OVHT" to "Overheating", "F-LOOS" to "Loose / misaligned", "F-CONT" to "Contaminated",
        "F-LOW" to "Low level / pressure", "F-WARN" to "Warning light / code", "F-OTH" to "Other",
    ),
    WoCodeKind.CAUSE to defaults(
        WoCodeKind.CAUSE,
        "C-NWEAR" to "Normal wear", "C-DMG" to "Accident / damage", "C-ABUSE" to "Operator abuse",
        "C-PMMISS" to "Missed maintenance", "C-DEFECT" to "Manufacturer defect",
        "C-ENV" to "Environment / road", "C-PRIOR" to "Prior repair", "C-UNK" to "Unknown",
    ),
    WoCodeKind.REMEDY to defaults(
        WoCodeKind.REMEDY,
        "R-REPL" to "Replaced", "R-REPR" to "Repaired", "R-ADJ" to "Adjusted", "R-CLEAN" to "Cleaned",
        "R-TOPUP" to "Topped up / refilled", "R-TIGHT" to "Tightened / secured",
        "R-RESET" to "Reset / reprogrammed", "R-NFF" to "No fault found", "R-DEFER" to "Deferred",
    ),
)

/** Parses `{codes:[...]}`; returns defaults for [kind] when empty or unparseable. */
/** Codes from the account's `reference_values`; a kind without account values uses [DEFAULT_WO_CODES]. */
fun woCodesFrom(
    failure: List<ReferenceValue>,
    cause: List<ReferenceValue>,
    remedy: List<ReferenceValue>,
): Map<WoCodeKind, List<WoCode>> = mapOf(
    WoCodeKind.FAILURE to failure,
    WoCodeKind.CAUSE to cause,
    WoCodeKind.REMEDY to remedy,
).mapValues { (kind, values) ->
    values.map { WoCode(it.id, kind, it.code, it.label, it.sortOrder) }
        .sortedBy { it.sortOrder }
        .ifEmpty { DEFAULT_WO_CODES[kind].orEmpty() }
}

fun parseWoCodes(body: JsonElement?, kind: WoCodeKind): List<WoCode> {
    val arr = ((body as? JsonObject)?.get("codes") as? JsonArray)
        ?: (body as? JsonArray)
    val parsed = arr?.mapIndexedNotNull { i, el ->
        val o = el as? JsonObject ?: return@mapIndexedNotNull null
        val code = o.str("code") ?: return@mapIndexedNotNull null
        val k = o.str("kind")
        if (k != null && k != kind.key) return@mapIndexedNotNull null
        if (o.bool("active") == false) return@mapIndexedNotNull null
        WoCode(o.str("code_id") ?: code, kind, code, o.str("label") ?: code, o.num("sort_order")?.toInt() ?: i)
    }?.sortedBy { it.sortOrder }
    return parsed?.takeIf { it.isNotEmpty() } ?: DEFAULT_WO_CODES.getValue(kind)
}

// ── SLA ─────────────────────────────────────────────────────────────────────

data class SlaWindow(val respondHours: Double, val resolveHours: Double)

/** Copy of web `DEFAULT_SLA_POLICY`. */
val DEFAULT_SLA_POLICY: Map<String, SlaWindow> = mapOf(
    "critical" to SlaWindow(1.0, 8.0),
    "high" to SlaWindow(4.0, 24.0),
    "medium" to SlaWindow(24.0, 72.0),
    "low" to SlaWindow(72.0, 240.0),
)

const val SLA_WARN_FRACTION = 0.75

/** Merges a server `{policy:{priority:{respond_hours,resolve_hours}}}` body over defaults. */
fun parseSlaPolicy(body: JsonElement?): Map<String, SlaWindow> {
    val policy = (body as? JsonObject)?.let { it.obj("policy") ?: it } ?: return DEFAULT_SLA_POLICY
    val out = DEFAULT_SLA_POLICY.toMutableMap()
    for ((prio, el) in policy) {
        val o = el as? JsonObject ?: continue
        val base = out[prio] ?: SlaWindow(Double.NaN, Double.NaN)
        val w = SlaWindow(o.num("respond_hours") ?: base.respondHours, o.num("resolve_hours") ?: base.resolveHours)
        if (!w.respondHours.isNaN() && !w.resolveHours.isNaN()) out[prio] = w
    }
    return out
}

/** Merges the account's synced `work_order_sla_policies` over defaults; null hours keep the default. */
fun slaPolicyOf(policies: List<SlaPolicy>): Map<String, SlaWindow> {
    val out = DEFAULT_SLA_POLICY.toMutableMap()
    for (p in policies) {
        val base = out[p.priority.wire] ?: SlaWindow(Double.NaN, Double.NaN)
        val w = SlaWindow(p.respondHours?.toDouble() ?: base.respondHours, p.resolveHours?.toDouble() ?: base.resolveHours)
        if (!w.respondHours.isNaN() && !w.resolveHours.isNaN()) out[p.priority.wire] = w
    }
    return out
}

enum class SlaPhase { RESPOND, RESOLVE, MET, NONE }
enum class SlaLevel { OK, WARN, BREACH }

data class SlaState(val phase: SlaPhase, val level: SlaLevel, val dueAtMs: Long?, val remainingMs: Long?)

/**
 * Respond window applies until the WO is assigned/in progress; resolve window until completed.
 * `attributes.sla.respond_hours/resolve_hours` override the policy.
 */
fun slaState(
    status: String?,
    priority: String?,
    createdAtMs: Long,
    completedAtMs: Long?,
    attributes: JsonObject,
    policy: Map<String, SlaWindow>,
    nowMs: Long,
): SlaState {
    val st = status?.lowercase() ?: ""
    if (st == "cancelled" || st == "draft") return SlaState(SlaPhase.NONE, SlaLevel.OK, null, null)
    val base = policy[priority?.lowercase() ?: "medium"] ?: policy["medium"]
        ?: return SlaState(SlaPhase.NONE, SlaLevel.OK, null, null)
    val override = attributes.obj(WO_KEY_SLA)
    val respondH = override?.num("respond_hours") ?: base.respondHours
    val resolveH = override?.num("resolve_hours") ?: base.resolveHours
    val respondDue = createdAtMs + (respondH * 3_600_000).toLong()
    val resolveDue = createdAtMs + (resolveH * 3_600_000).toLong()

    if (st == "completed" || st == "complete") {
        val done = completedAtMs ?: nowMs
        val level = if (done > resolveDue) SlaLevel.BREACH else SlaLevel.OK
        return SlaState(SlaPhase.MET, level, resolveDue, null)
    }
    val responded = st in setOf("assigned", "in_progress", "on_hold")
    val (phase, due, hours) = if (responded) Triple(SlaPhase.RESOLVE, resolveDue, resolveH)
    else Triple(SlaPhase.RESPOND, respondDue, respondH)
    val remaining = due - nowMs
    val windowMs = hours * 3_600_000
    val level = when {
        remaining < 0 -> SlaLevel.BREACH
        nowMs - createdAtMs >= windowMs * SLA_WARN_FRACTION -> SlaLevel.WARN
        else -> SlaLevel.OK
    }
    return SlaState(phase, level, due, remaining)
}

// ── Timer ───────────────────────────────────────────────────────────────────

/** The signed-in worker's labour clock on one work order, from their `time_entries`. */
data class WoTimerState(
    val running: Boolean,
    val startedAtMs: Long?,
    /** Seconds of finished entries. */
    val totalSeconds: Long,
) {
    fun elapsedSeconds(nowMs: Long): Long =
        totalSeconds + if (running && startedAtMs != null) ((nowMs - startedAtMs) / 1000).coerceAtLeast(0) else 0

    companion object {
        val IDLE = WoTimerState(false, null, 0)

        /** Timer state from [mine], the signed-in worker's entries on the work order. */
        fun of(mine: List<TimeEntry>): WoTimerState {
            val open = mine.filter { it.isRunning }.maxByOrNull { it.start }
            val total = mine.filter { !it.isRunning }.sumOf { e ->
                (((e.end ?: e.start) - e.start) / 1000 - e.breakMinutes * 60L).coerceAtLeast(0)
            }
            return WoTimerState(open != null, open?.start, total)
        }
    }
}
