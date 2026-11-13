package com.avago.feature.log.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.round

/**
 * Log attribute field packs (contract §1). Keys are identical across web / iOS / Android.
 */
object LogPacks {

    enum class Pack { FUEL, OIL, TIRE, BATTERY, BRAKE }

    private val FUEL_RE = Regex("fuel|refuel|gas|diesel|def\\b", RegexOption.IGNORE_CASE)
    private val OIL_RE = Regex("oil", RegexOption.IGNORE_CASE)
    private val TIRE_RE = Regex("tire|tyre|wheel", RegexOption.IGNORE_CASE)
    private val BATTERY_RE = Regex("batter", RegexOption.IGNORE_CASE)
    private val BRAKE_RE = Regex("brake", RegexOption.IGNORE_CASE)

    /** Fuel-system service categories (filters, injectors) are maintenance, not fuel fills. */
    private val NOT_FUEL_RE = Regex("fuel[\\s_-]*filter|fuel[\\s_-]*system|injector", RegexOption.IGNORE_CASE)
    private val NOT_TIRE_RE = Regex("alignment", RegexOption.IGNORE_CASE)

    /**
     * Packs matching a category key/label. Fuel wins exclusively (a fuel log is never an oil log).
     * Fuel-filter / fuel-system / injector categories are not fuel (and get no pack); wheel
     * alignment is not a tire pack.
     */
    fun packsFor(category: String?): Set<Pack> {
        if (category.isNullOrBlank()) return emptySet()
        val notFuel = NOT_FUEL_RE.containsMatchIn(category)
        if (!notFuel && FUEL_RE.containsMatchIn(category)) return setOf(Pack.FUEL)
        if (notFuel) return emptySet()
        val out = mutableSetOf<Pack>()
        if (OIL_RE.containsMatchIn(category)) out += Pack.OIL
        if (TIRE_RE.containsMatchIn(category) && !NOT_TIRE_RE.containsMatchIn(category)) out += Pack.TIRE
        if (BATTERY_RE.containsMatchIn(category)) out += Pack.BATTERY
        if (BRAKE_RE.containsMatchIn(category)) out += Pack.BRAKE
        return out
    }

    fun isFuel(category: String?): Boolean = Pack.FUEL in packsFor(category)

    // ── Enumerations ────────────────────────────────────────────────────────
    val FUEL_VOLUME_UNITS = listOf("gallon", "liter", "kWh")

    /** Canonical fuel_volume_unit (gallon | liter | kWh); also reads legacy gal / L. */
    fun normalizeVolumeUnit(u: String?): String? = when (u?.trim()?.lowercase()) {
        null, "" -> null
        "gal", "gals", "gallon", "gallons" -> "gallon"
        "l", "liter", "liters", "litre", "litres" -> "liter"
        "kwh" -> "kWh"
        else -> u.trim()
    }

    val FUEL_GRADES = listOf("regular", "midgrade", "premium", "diesel", "e85", "def", "cng", "electric")
    val OIL_TYPES = listOf("conventional", "synthetic_blend", "full_synthetic", "high_mileage", "diesel")
    val OIL_QTY_UNITS = listOf("qt", "L")
    val TIRE_ACTIONS = listOf("rotate", "replace", "repair", "inspect", "balance")
    val TIRE_POSITIONS = listOf("fl", "fr", "rl", "rr", "rlo", "rli", "rro", "rri", "spare")
    val TIRE_TREAD_UNITS = listOf("32nds", "mm")
    val TIRE_PRESSURE_UNITS = listOf("psi", "kPa", "bar")
    val BATTERY_ACTIONS = listOf("test", "replace", "charge")
    val BATTERY_RESULTS = listOf("good", "good_recharge", "recharge_retest", "replace", "bad_cell")
    val BRAKE_AXLES = listOf("front", "rear", "all")
    val BRAKE_FLUID = listOf("ok", "low", "replaced", "contaminated")
    val BRAKE_POSITIONS = listOf("fl", "fr", "rl", "rr")

    /** Keys written by the pack editors; used to clear stale values when a pack field is emptied. */
    val PACK_KEYS: Map<Pack, List<String>> = mapOf(
        Pack.FUEL to listOf(
            "fuel_volume", "fuel_volume_unit", "fuel_price_per_unit", "fuel_grade", "fuel_station",
            "fuel_full_fill", "fuel_card_last4", "fuel_economy", "fuel_economy_unit",
        ),
        Pack.OIL to listOf(
            "oil_type", "oil_viscosity", "oil_quantity", "oil_quantity_unit", "oil_filter_part",
            "oil_next_due_meter", "oil_next_due_date",
        ),
        Pack.TIRE to listOf(
            "tire_action", "tire_positions", "tire_brand", "tire_size", "tire_dot_date",
            "tire_tread", "tire_tread_unit", "tire_psi", "tire_pressure_unit",
        ),
        Pack.BATTERY to listOf(
            "battery_action", "battery_rated_cca", "battery_measured_cca", "battery_voltage",
            "battery_test_result", "battery_date_code",
        ),
        Pack.BRAKE to listOf("brake_axle", "brake_pad_mm", "brake_rotor_mm", "brake_fluid"),
    )

    // ── Fuel math ───────────────────────────────────────────────────────────

    data class FuelTriple(val pricePerUnit: Double?, val volume: Double?, val total: Double?)

    /**
     * Given any two of price/volume/total compute the third. [lastEdited] (one of "price",
     * "volume", "total") is never overwritten; when all three are present the most-stale
     * value is recomputed from the other two.
     */
    fun solveFuel(input: FuelTriple, lastEdited: String? = null, recomputeOrder: List<String> = listOf("total", "price", "volume")): FuelTriple {
        val p = input.pricePerUnit?.takeIf { it > 0 }
        val v = input.volume?.takeIf { it > 0 }
        val t = input.total?.takeIf { it > 0 }
        val present = listOfNotNull(p?.let { "price" }, v?.let { "volume" }, t?.let { "total" })
        if (present.size < 2) return input
        val target = if (present.size == 2) {
            listOf("price", "volume", "total").first { it !in present }
        } else {
            recomputeOrder.firstOrNull { it != lastEdited } ?: return input
        }
        return when (target) {
            "total" -> input.copy(total = round2(p!! * v!!))
            "price" -> input.copy(pricePerUnit = round3(t!! / v!!))
            else -> input.copy(volume = round3(t!! / p!!))
        }
    }

    data class FuelInputs(val price: String, val volume: String, val total: String, val derived: String?)

    /**
     * Applies a user edit of [field] ("price" | "volume" | "total") to the fuel triple.
     * The field auto-computed from the other two is tracked in [FuelInputs.derived]; editing
     * a source re-derives it, editing the derived field itself hands control back to the user.
     */
    fun applyFuelEdit(prev: FuelInputs, field: String, value: String): FuelInputs {
        var s = when (field) {
            "price" -> prev.copy(price = value)
            "volume" -> prev.copy(volume = value)
            else -> prev.copy(total = value)
        }
        if (s.derived == field) s = s.copy(derived = null)
        fun d(v: String) = v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        val target = s.derived ?: run {
            val blanks = listOf("price" to s.price, "volume" to s.volume, "total" to s.total)
                .filter { d(it.second) == null }
            if (blanks.size == 1 && blanks.first().first != field) blanks.first().first else return s
        }
        val triple = FuelTriple(
            if (target == "price") null else d(s.price),
            if (target == "volume") null else d(s.volume),
            if (target == "total") null else d(s.total),
        )
        val solved = solveFuel(triple)
        val out = when (target) {
            "price" -> solved.pricePerUnit
            "volume" -> solved.volume
            else -> solved.total
        }
        val text = out?.let { formatNumber(it) } ?: ""
        return when (target) {
            "price" -> s.copy(price = text, derived = target)
            "volume" -> s.copy(volume = text, derived = target)
            else -> s.copy(total = text, derived = target)
        }
    }

    /** One fuel entry, used for economy computation. */
    data class FuelFill(
        val entryId: String,
        val dateMs: Long,
        val meter: Double?,
        val volume: Double?,
        val fullFill: Boolean,
    )

    fun economyUnitFor(volumeUnit: String?, distanceUnit: String? = null): String {
        val km = distanceUnit?.lowercase()?.startsWith("km") == true
        return when (normalizeVolumeUnit(volumeUnit)) {
            "liter" -> if (km || distanceUnit == null) "km/L" else "mpg"
            "kWh" -> "mi/kWh"
            else -> "mpg"
        }
    }

    /**
     * Economy = distance since the previous FULL fill / volume summed since then (partial fills
     * in-between accumulate, including the current fill). Only computed when [current] is a full
     * fill. Returns null when no previous full fill with a lower meter exists.
     */
    fun computeEconomy(current: FuelFill, history: List<FuelFill>, unit: String = "mpg"): Double? {
        if (!current.fullFill) return null
        val curMeter = current.meter ?: return null
        val curVol = current.volume?.takeIf { it > 0 } ?: return null
        val earlier = history
            .filter { it.entryId != current.entryId && it.meter != null }
            .filter { it.meter!! < curMeter || (it.meter == curMeter && it.dateMs < current.dateMs) }
            .sortedWith(compareByDescending<FuelFill> { it.meter }.thenByDescending { it.dateMs })
        val prevFull = earlier.firstOrNull { it.fullFill } ?: return null
        val prevMeter = prevFull.meter!!
        val distance = curMeter - prevMeter
        if (distance <= 0) return null
        val partials = earlier.takeWhile { it !== prevFull }
            .filter { !it.fullFill && it.meter!! > prevMeter }
            .sumOf { it.volume ?: 0.0 }
        val totalVol = curVol + partials
        if (totalVol <= 0) return null
        val econ = if (unit == "L/100km") totalVol / distance * 100.0 else distance / totalVol
        return round2(econ)
    }

    // ── Attribute merge ─────────────────────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String?): JsonObject {
        if (raw.isNullOrBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
    }

    /**
     * Merges [patch] over [existing]; null / JsonNull values remove the key.
     * Every other key from [existing] (unknown or from other packs) is preserved.
     */
    fun merge(existing: JsonObject, patch: Map<String, JsonElement?>): JsonObject {
        val out = LinkedHashMap(existing)
        for ((k, v) in patch) if (v == null || v is JsonNull) out.remove(k) else out[k] = v
        return JsonObject(out)
    }

    /** String form value → typed JSON (number or boolean when parseable, else string). Blank → null. */
    fun typed(value: String?): JsonElement? {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return null
        v.toDoubleOrNull()?.let { return JsonPrimitive(it) }
        if (v.equals("true", true)) return JsonPrimitive(true)
        if (v.equals("false", true)) return JsonPrimitive(false)
        return JsonPrimitive(v)
    }

    fun num(value: String?): JsonElement? = value?.trim()?.replace(',', '.')?.toDoubleOrNull()?.let { JsonPrimitive(it) }

    fun str(value: String?): JsonElement? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) }

    fun numMap(values: Map<String, String>): JsonElement? {
        val m = values.mapNotNull { (k, v) -> v.trim().replace(',', '.').toDoubleOrNull()?.let { k to JsonPrimitive(it) } }
        return if (m.isEmpty()) null else JsonObject(m.toMap())
    }

    fun strList(values: Collection<String>): JsonElement? =
        if (values.isEmpty()) null else JsonArray(values.map { JsonPrimitive(it) })

    // ── Readers ─────────────────────────────────────────────────────────────

    fun JsonObject.readString(key: String): String? {
        val el = this[key] as? JsonPrimitive ?: return null
        if (el is JsonNull) return null
        el.doubleOrNull?.let { d -> if (!el.isString) return formatNumber(d) }
        return el.contentOrNull
    }

    fun JsonObject.readDouble(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    fun JsonObject.readBool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

    fun JsonObject.readStringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    fun JsonObject.readNumMap(key: String): Map<String, String> =
        (this[key] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.doubleOrNull?.let { k to formatNumber(it) }
        }?.toMap() ?: emptyMap()

    fun formatNumber(d: Double): String =
        if (abs(d - d.toLong()) < 1e-9) d.toLong().toString() else d.toString()

    private fun round2(d: Double) = round(d * 100) / 100
    private fun round3(d: Double) = round(d * 1000) / 1000

    /** Validates a WWYY tire DOT date code (e.g. "0723"). */
    fun isValidDotDate(v: String): Boolean {
        if (!Regex("^\\d{4}$").matches(v)) return false
        val week = v.substring(0, 2).toInt()
        return week in 1..53
    }
}
