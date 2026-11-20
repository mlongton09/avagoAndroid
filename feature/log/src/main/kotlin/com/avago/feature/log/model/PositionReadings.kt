package com.avago.feature.log.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.max
import kotlin.math.min

/**
 * Per-position (wheel) readings around the vehicle sketch — port of web
 * `components/log/forms/PositionReadings.tsx` + the position / threshold helpers in
 * `lib/log/fieldPacks.ts` and `lib/log/thresholds.ts`.
 *
 * Data contract is unchanged (identical keys on web / iOS / Android): `tire_positions` (string
 * list), `tire_tread` / `tire_psi` / `brake_pad_mm` / `brake_rotor_mm` (`{position: number}`),
 * `tire_tread_unit`, `tire_pressure_unit`, `brake_axle`. Nothing here is persisted — states are
 * derived for display only.
 */
object PositionReadings {

    /** Web `ReadingState`: a reading is either inside its default window or out of range. */
    enum class ReadingState { OK, OUT }

    /** Web `WheelState` for a sketch corner. */
    enum class WheelState { EMPTY, OK, OUT }

    val BASE_POSITIONS = listOf("fl", "fr", "rl", "rr")
    val DUAL_POSITIONS = listOf("fl", "fr", "rlo", "rli", "rri", "rro")
    const val SPARE = "spare"

    /** Canonical position order (matches [LogPacks.TIRE_POSITIONS]). */
    private val ORDER = LogPacks.TIRE_POSITIONS

    /** Left-hand column of the layout (web `LEFT`). */
    private val LEFT = setOf("fl", "rl", "rlo", "rli")

    // ── Asset profile / thresholds (web thresholds.ts) ─────────────────────────

    enum class Profile { LIGHT, PICKUP, HEAVY, RV, TRAILER, FORKLIFT, MOTORCYCLE, ATV, GOLF, EQUIPMENT, GENERIC }

    private val PROFILE_BY_TYPE: Map<String, Profile> = buildMap {
        listOf("car", "ev", "lightvehicle", "suv", "van").forEach { put(it, Profile.LIGHT) }
        listOf("truck", "pickup").forEach { put(it, Profile.PICKUP) }
        listOf("commercialvehicle", "heavytruck", "bus").forEach { put(it, Profile.HEAVY) }
        listOf("recreationalvehicle", "recreationvehicle", "rv", "motorhome").forEach { put(it, Profile.RV) }
        put("trailer", Profile.TRAILER)
        put("forklift", Profile.FORKLIFT)
        put("motorcycle", Profile.MOTORCYCLE)
        listOf("atvutv", "atv", "utv").forEach { put(it, Profile.ATV) }
        put("golfcart", Profile.GOLF)
        listOf("equipment", "heavyequipment", "lawnequipment", "crane").forEach { put(it, Profile.EQUIPMENT) }
    }

    /** Web `assetProfile`: accepts web ids (`light_vehicle`) and iOS ids (`LightVehicle`). */
    fun profile(assetType: String?): Profile =
        PROFILE_BY_TYPE[(assetType ?: "").lowercase().filter { it in 'a'..'z' }] ?: Profile.GENERIC

    /** Cold inflation window in psi per profile (web `TIRE_PSI`). */
    val TIRE_PSI: Map<Profile, Pair<Double, Double>> = mapOf(
        Profile.LIGHT to (28.0 to 45.0),
        Profile.PICKUP to (30.0 to 80.0),
        Profile.HEAVY to (85.0 to 120.0),
        Profile.RV to (50.0 to 110.0),
        Profile.TRAILER to (85.0 to 110.0),
        Profile.FORKLIFT to (80.0 to 120.0),
        Profile.MOTORCYCLE to (28.0 to 42.0),
        Profile.ATV to (3.0 to 15.0),
        Profile.GOLF to (15.0 to 30.0),
        Profile.EQUIPMENT to (15.0 to 130.0),
        Profile.GENERIC to (10.0 to 150.0),
    )

    /** Minimum serviceable brake pad lining (web `BRAKE_PAD_MIN_MM`). */
    const val BRAKE_PAD_MIN_MM = 3.0

    private const val PSI_TO_KPA = 6.894757

    data class Range(val min: Double, val max: Double) {
        fun contains(v: Double) = v >= min && v <= max
    }

    /** Web `normalizeUnit` (subset relevant to readings). */
    fun normalizeUnit(raw: String?): String? {
        val u = (raw ?: "").trim().lowercase().replace(Regex("[\\s.]"), "")
        return when (u) {
            "" -> null
            "psi" -> "psi"
            "kpa" -> "kpa"
            "bar" -> "bar"
            "32nds", "/32", "32nd", "1/32in" -> "32nds"
            "mm" -> "mm"
            "in", "inch", "inches", "\"" -> "in"
            "%", "percent" -> "%"
            else -> null
        }
    }

    private fun convertPsi(psi: Double, unit: String): Double = when (unit) {
        "kpa" -> Math.round(psi * PSI_TO_KPA / 5.0) * 5.0
        "bar" -> Math.round(psi * PSI_TO_KPA) / 100.0
        else -> psi
    }

    /** Pressure window for [unit] (psi / kPa / bar) and asset type; null for unknown units. */
    fun pressureRange(unit: String?, assetType: String?): Range? {
        val u = normalizeUnit(unit) ?: "psi"
        if (u != "psi" && u != "kpa" && u != "bar") return null
        val (lo, hi) = TIRE_PSI.getValue(profile(assetType))
        return Range(convertPsi(lo, u), convertPsi(hi, u))
    }

    /** Legal tread window (FMCSA 393.75 / EU 1.6 mm): steer axle (front) is stricter. */
    fun treadRange(unit: String?, front: Boolean): Range? = when (normalizeUnit(unit) ?: "32nds") {
        "32nds" -> Range(if (front) 4.0 else 2.0, 32.0)
        "mm" -> Range(if (front) 3.0 else 1.6, 25.0)
        "in" -> Range(if (front) 0.125 else 0.0625, 1.0)
        else -> null
    }

    fun isFront(pos: String) = pos == "fl" || pos == "fr"

    fun treadState(value: Double?, unit: String?, pos: String): ReadingState? {
        value ?: return null
        val r = treadRange(unit, isFront(pos)) ?: return ReadingState.OK
        return if (r.contains(value)) ReadingState.OK else ReadingState.OUT
    }

    fun pressureState(value: Double?, unit: String?, assetType: String?): ReadingState? {
        value ?: return null
        val r = pressureRange(unit, assetType) ?: return ReadingState.OK
        return if (r.contains(value)) ReadingState.OK else ReadingState.OUT
    }

    fun padState(value: Double?): ReadingState? {
        value ?: return null
        return if (value < BRAKE_PAD_MIN_MM) ReadingState.OUT else ReadingState.OK
    }

    // ── Positions (web fieldPacks.ts) ──────────────────────────────────────────

    /** Dual rear wheels on heavy trucks and buses, four corners otherwise. */
    fun positionsFor(assetType: String?, withSpare: Boolean = false): List<String> {
        val base = if (profile(assetType) == Profile.HEAVY) DUAL_POSITIONS else BASE_POSITIONS
        return if (withSpare) base + SPARE else base
    }

    fun positionsForAxle(axle: String?, all: List<String> = BASE_POSITIONS): List<String> = when (axle) {
        "front" -> all.filter { isFront(it) }
        "rear" -> all.filter { !isFront(it) && it != SPARE }
        else -> all.filter { it != SPARE }
    }

    /** Sketch corner a position tints (duals share their side's corner); spare → null. */
    fun cornerOf(pos: String): String? = when (pos) {
        "fl", "fr", "rl", "rr" -> pos
        "rlo", "rli" -> "rl"
        "rro", "rri" -> "rr"
        else -> null
    }

    /**
     * Positions to render: the asset's [base] positions plus any position that already has a
     * selection or a stored reading (so data entered on another client is never hidden), in
     * canonical order.
     */
    fun displayPositions(base: List<String>, withData: Collection<String>): List<String> {
        val known = (base + withData.filter { it in ORDER }).toSet()
        return ORDER.filter { it in known }
    }

    /** Web grid: left column / right column (rows aligned), spare below the left column. */
    data class Layout(val left: List<String>, val right: List<String>, val spare: Boolean) {
        val rows: Int get() = max(max(left.size, right.size), 1)
    }

    fun layout(positions: List<String>): Layout {
        val wheels = positions.filter { it != SPARE }
        return Layout(
            left = wheels.filter { it in LEFT },
            right = wheels.filter { it !in LEFT },
            spare = SPARE in positions,
        )
    }

    // ── States ─────────────────────────────────────────────────────────────────

    /** One measured quantity per position (tread, pressure, pad, rotor). */
    class Metric(
        val key: String,
        val values: Map<String, Double>,
        val state: ((Double?, String) -> ReadingState?)? = null,
    )

    /** Web `posState`: any OUT wins, else OK when a metric has a judged reading. */
    fun positionState(metrics: List<Metric>, pos: String): ReadingState? {
        var s: ReadingState? = null
        for (m in metrics) {
            when (m.state?.invoke(m.values[pos], pos)) {
                ReadingState.OUT -> return ReadingState.OUT
                ReadingState.OK -> s = ReadingState.OK
                null -> Unit
            }
        }
        return s
    }

    /**
     * Web `corners`: per sketch corner, OUT beats OK; a selected position without readings
     * tints its corner OK. Corners with nothing are omitted (= [WheelState.EMPTY]).
     */
    fun cornerStates(positions: List<String>, metrics: List<Metric>, selected: Collection<String>? = null): Map<String, WheelState> {
        val out = LinkedHashMap<String, WheelState>()
        for (p in positions) {
            if (p == SPARE) continue
            val c = cornerOf(p) ?: continue
            val st = positionState(metrics, p)
            when {
                st == ReadingState.OUT -> out[c] = WheelState.OUT
                st == ReadingState.OK && out[c] != WheelState.OUT -> out[c] = WheelState.OK
                out[c] == null && selected?.contains(p) == true -> out[c] = WheelState.OK
            }
        }
        return out
    }

    /** Web `wheelState`: state of a drawn wheel tinted by [corners]; null = decorative. */
    fun wheelState(corners: List<String>, states: Map<String, WheelState>): WheelState? {
        if (corners.isEmpty()) return null
        val list = corners.map { states[it] ?: WheelState.EMPTY }
        return when {
            WheelState.OUT in list -> WheelState.OUT
            WheelState.OK in list -> WheelState.OK
            else -> WheelState.EMPTY
        }
    }

    /** Positions drawn at a sketch corner (duals → both). */
    fun positionsAt(corner: String, positions: List<String>): List<String> = positions.filter { cornerOf(it) == corner }

    /** Form text → number (comma decimal accepted); blank / invalid → null (web `setRecordValue`). */
    fun parse(raw: String?): Double? = raw?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun parseAll(values: Map<String, String>?): Map<String, Double> =
        values.orEmpty().mapNotNull { (k, v) -> parse(v)?.let { k to it } }.toMap()

    /** Display unit (web `tUnit`/`displayUnit`): `32nds` → `/32"`, `kpa` → `kPa`. */
    fun displayUnit(raw: String?): String = when (normalizeUnit(raw)) {
        "psi" -> "psi"
        "kpa" -> "kPa"
        "bar" -> "bar"
        "32nds" -> "/32\""
        "mm" -> "mm"
        "in" -> "in"
        "%" -> "%"
        else -> raw.orEmpty()
    }

    // ── Pack wiring (tire / brake) ─────────────────────────────────────────────

    enum class PackKind { TIRE, BRAKE }

    /** Metric key, stored attribute key and display unit for one input column. */
    data class MetricSpec(val key: String, val attrKey: String, val unit: String)

    /** Everything a readings panel needs, derived from raw attribute values. */
    data class Panel(
        val pack: PackKind,
        val positions: List<String>,
        val specs: List<MetricSpec>,
        val metrics: List<Metric>,
        /** Tire: selected positions (`tire_positions`); brake: null (no selection). */
        val selected: Set<String>?,
        val corners: Map<String, WheelState>,
    ) {
        val layout: Layout get() = layout(positions)
        fun state(pos: String): ReadingState? = positionState(metrics, pos)
        fun metricState(key: String, pos: String): ReadingState? =
            metrics.firstOrNull { it.key == key }?.let { m -> m.state?.invoke(m.values[pos], pos) }
        /** Positions with something to show (read-only views). */
        fun withData(): List<String> =
            positions.filter { p -> (selected?.contains(p) == true) || metrics.any { p in it.values } }
    }

    /**
     * Tire panel (web `TireFields`): every asset position + spare with a selection toggle and
     * tread / pressure inputs; units default to `32nds` / `psi`.
     */
    fun tirePanel(
        assetType: String?,
        selected: Collection<String>,
        tread: Map<String, Double>,
        psi: Map<String, Double>,
        treadUnit: String?,
        pressureUnit: String?,
    ): Panel {
        val tu = treadUnit?.takeIf { it.isNotBlank() } ?: "32nds"
        val pu = pressureUnit?.takeIf { it.isNotBlank() } ?: "psi"
        val positions = displayPositions(positionsFor(assetType, withSpare = true), selected + tread.keys + psi.keys)
        val metrics = listOf(
            Metric("tread", tread) { v, pos -> treadState(v, tu, pos) },
            Metric("psi", psi) { v, _ -> pressureState(v, pu, assetType) },
        )
        val sel = selected.toSet()
        return Panel(
            pack = PackKind.TIRE,
            positions = positions,
            specs = listOf(
                MetricSpec("tread", "tire_tread", displayUnit(tu)),
                MetricSpec("psi", "tire_psi", displayUnit(pu)),
            ),
            metrics = metrics,
            selected = sel,
            corners = cornerStates(positions, metrics, sel),
        )
    }

    /** Brake panel (web `BrakeFields`): positions filtered by `brake_axle`; pads judged vs 3 mm. */
    fun brakePanel(assetType: String?, axle: String?, pads: Map<String, Double>, rotors: Map<String, Double>): Panel {
        val base = positionsForAxle(axle?.takeIf { it.isNotBlank() } ?: "all", positionsFor(assetType))
        val positions = displayPositions(base, pads.keys + rotors.keys).filter { it != SPARE }
        val metrics = listOf(
            Metric("pad", pads) { v, _ -> padState(v) },
            Metric("rotor", rotors),
        )
        return Panel(
            pack = PackKind.BRAKE,
            positions = positions,
            specs = listOf(MetricSpec("pad", "brake_pad_mm", "mm"), MetricSpec("rotor", "brake_rotor_mm", "mm")),
            metrics = metrics,
            selected = null,
            corners = cornerStates(positions, metrics, null),
        )
    }

    private fun JsonObject.record(key: String): Map<String, Double> =
        (this[key] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.let { it.doubleOrNull ?: parse(it.contentOrNull) }?.let { k to it }
        }?.toMap() ?: emptyMap()

    private fun JsonObject.list(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        else -> emptyList()
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Read-only tire panel from stored attributes; null when there is no position data. */
    fun tireFromAttrs(attrs: JsonObject, assetType: String?): Panel? {
        val sel = attrs.list("tire_positions")
        val tread = attrs.record("tire_tread")
        val psi = attrs.record("tire_psi")
        if (sel.isEmpty() && tread.isEmpty() && psi.isEmpty()) return null
        return tirePanel(assetType, sel, tread, psi, attrs.text("tire_tread_unit"), attrs.text("tire_pressure_unit"))
    }

    /** Read-only brake panel from stored attributes; null when there are no readings. */
    fun brakeFromAttrs(attrs: JsonObject, assetType: String?): Panel? {
        val pads = attrs.record("brake_pad_mm")
        val rotors = attrs.record("brake_rotor_mm")
        if (pads.isEmpty() && rotors.isEmpty()) return null
        return brakePanel(assetType, attrs.text("brake_axle"), pads, rotors)
    }

    // ── Sketch hit targets ─────────────────────────────────────────────────────

    /** Tap / TalkBack target for one sketch corner, in viewBox units. */
    data class Target(val corner: String, val corners: List<String>, val x: Float, val y: Float, val w: Float, val h: Float) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
        fun overlaps(o: Target) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
    }

    /**
     * One target per tinted corner of [kind] (union of its wheels), grown to [minSize] viewBox
     * units and split at the midpoint where two targets would overlap (trailer axles sit close).
     * A wheel shared by several corners (motorcycle) targets its first corner.
     */
    fun wheelTargets(kind: VehicleSketchModel.Kind, minSize: Float): List<Target> {
        val wheels = VehicleSketchModel.outline(kind).wheels.filter { it.corners.isNotEmpty() }
        val byCorner = LinkedHashMap<String, MutableList<VehicleSketchModel.Wheel>>()
        wheels.forEach { w -> byCorner.getOrPut(w.corners.first()) { mutableListOf() } += w }
        val targets = byCorner.map { (corner, ws) ->
            val x0 = ws.minOf { it.x }; val y0 = ws.minOf { it.y }
            val x1 = ws.maxOf { it.x + it.w }; val y1 = ws.maxOf { it.y + it.h }
            val w = max(minSize, x1 - x0); val h = max(minSize, y1 - y0)
            Target(corner, ws.flatMap { it.corners }.distinct(), (x0 + x1) / 2f - w / 2f, (y0 + y1) / 2f - h / 2f, w, h)
        }.toMutableList()
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val a = targets[i]; val b = targets[j]
            if (!a.overlaps(b)) continue
            val dx = kotlin.math.abs(a.cx - b.cx); val dy = kotlin.math.abs(a.cy - b.cy)
            if (dy >= dx) {
                val (top, bottom, ti, bi) = if (a.cy <= b.cy) Quad(a, b, i, j) else Quad(b, a, j, i)
                val mid = (top.cy + bottom.cy) / 2f
                targets[ti] = top.copy(h = min(top.h, mid - top.y))
                targets[bi] = bottom.copy(y = max(bottom.y, mid), h = bottom.y + bottom.h - max(bottom.y, mid))
            } else {
                val (l, r, li, ri) = if (a.cx <= b.cx) Quad(a, b, i, j) else Quad(b, a, j, i)
                val mid = (l.cx + r.cx) / 2f
                targets[li] = l.copy(w = min(l.w, mid - l.x))
                targets[ri] = r.copy(x = max(r.x, mid), w = r.x + r.w - max(r.x, mid))
            }
        }
        return targets
    }

    private data class Quad(val a: Target, val b: Target, val ai: Int, val bi: Int)
}
