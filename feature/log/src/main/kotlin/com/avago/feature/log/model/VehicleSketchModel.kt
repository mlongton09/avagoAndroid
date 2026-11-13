package com.avago.feature.log.model

import kotlin.math.max
import kotlin.math.min

/**
 * Platform-neutral model of the web top-down vehicle sketch (`src/components/log/VehicleSketch.tsx`).
 *
 * Data contract (identical to web / iOS): a defect's position on the sketch is the **zone id**
 * stored in the inspection values as `<itemId>__loc` (one of [InspectionV2.LOCATIONS]) and copied
 * into each envelope `defects[].location`. There are no x/y coordinates in the contract — pins are
 * laid out at the zone centre by [layout], so a mark drawn on any client renders in the same zone
 * on every other client. Moving a pin rewrites `__loc` ([moveValue]); nothing else is persisted.
 *
 * Geometry is in the web SVG viewBox (`0 0 120 220`, front of the vehicle at the top).
 */
object VehicleSketchModel {
    const val VIEW_W = 120f
    const val VIEW_H = 220f

    /** Pin radius in viewBox units (used for spacing / clamping). */
    const val PIN_R = 6f
    /** Spacing between pins sharing a zone, in viewBox units. */
    const val PIN_STEP = 13f

    val WHEEL_CORNERS = listOf("fl", "fr", "rl", "rr")
    /** Contract locations that are not drawn on the outline (offered as chips, web `LocationPicker`). */
    val OFF_SKETCH_LOCATIONS = listOf("roof", "under", "interior", "engine")

    enum class Kind { SEDAN, EV, SUV, PICKUP, BOX_TRUCK, MOTORHOME, TRAILER, UTV, GOLF, MOTO, LOADER, FORKLIFT, MOWER, CRANE }

    private val KIND_BY_TYPE = mapOf(
        "car" to Kind.SEDAN,
        "ev" to Kind.EV,
        "lightvehicle" to Kind.SUV,
        "truck" to Kind.PICKUP,
        "commercialvehicle" to Kind.BOX_TRUCK,
        "recreationalvehicle" to Kind.MOTORHOME,
        "trailer" to Kind.TRAILER,
        "generator" to Kind.TRAILER,
        "compressor" to Kind.TRAILER,
        "atvutv" to Kind.UTV,
        "golfcart" to Kind.GOLF,
        "motorcycle" to Kind.MOTO,
        "equipment" to Kind.LOADER,
        "heavyequipment" to Kind.LOADER,
        "forklift" to Kind.FORKLIFT,
        "lawnequipment" to Kind.MOWER,
        "crane" to Kind.CRANE,
    )

    /** Web `sketchKindFor`: accepts web ids (`light_vehicle`) and iOS ids (`LightVehicle`). */
    fun kindFor(assetType: String?): Kind {
        val key = (assetType ?: "").lowercase().filter { it in 'a'..'z' }
        return KIND_BY_TYPE[key] ?: Kind.SEDAN
    }

    private val BED_KINDS = setOf(Kind.PICKUP, Kind.BOX_TRUCK, Kind.UTV)

    fun hasBed(kind: Kind): Boolean = kind in BED_KINDS
    fun hasBed(assetType: String?): Boolean = hasBed(kindFor(assetType))

    // ------------------------------------------------------------------
    // Outline geometry (verbatim from VehicleSketch.tsx)
    // ------------------------------------------------------------------

    sealed interface Shape {
        /** SVG path data (absolute M / L / Q / Z). [open] = web `fill="none"` on a body path. */
        data class Path(val d: String, val open: Boolean = false) : Shape
        data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float = 0f, val dashed: Boolean = false) : Shape
        data class Circle(val cx: Float, val cy: Float, val r: Float) : Shape
        data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Shape
    }

    data class Wheel(
        val x: Float,
        val y: Float,
        val w: Float = 12f,
        val h: Float = 26f,
        /** Corners whose readings tint this wheel; empty = decorative axle. */
        val corners: List<String> = emptyList(),
        val knobby: Boolean = false,
    ) {
        val rx: Float get() = min(4f, w / 3f)
        /** Tread lines for knobby tyres: (x1, y, x2). */
        fun treads(): List<Triple<Float, Float, Float>> =
            if (!knobby) emptyList() else (0 until (h / 6f).toInt()).map { k -> Triple(x + 2f, y + 4f + k * 6f, x + w - 2f) }
    }

    data class Outline(val body: List<Shape>, val detail: List<Shape>, val wheels: List<Wheel>)

    private fun four(front: Float, rear: Float, left: Float, right: Float, w: Float = 12f, h: Float = 26f) = listOf(
        Wheel(left, front, w, h, listOf("fl")),
        Wheel(right, front, w, h, listOf("fr")),
        Wheel(left, rear, w, h, listOf("rl")),
        Wheel(right, rear, w, h, listOf("rr")),
    )

    private fun p(d: String) = Shape.Path(d)
    private fun r(x: Number, y: Number, w: Number, h: Number, rx: Number = 0, dashed: Boolean = false) =
        Shape.Rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), rx.toFloat(), dashed)
    private fun c(cx: Number, cy: Number, rad: Number) = Shape.Circle(cx.toFloat(), cy.toFloat(), rad.toFloat())

    private val OUTLINES: Map<Kind, Outline> = mapOf(
        Kind.SEDAN to Outline(
            body = listOf(p("M42 12 Q60 6 78 12 Q94 16 94 42 L94 184 Q94 204 78 208 Q60 212 42 208 Q26 204 26 184 L26 42 Q26 16 42 12 Z")),
            detail = listOf(
                p("M34 66 Q60 56 86 66 L82 88 Q60 82 38 88 Z"),
                r(38, 92, 44, 54, 6),
                p("M38 150 Q60 155 82 150 L86 170 Q60 178 34 170 Z"),
                p("M44 22 Q60 17 76 22 M30 22 L40 15 M90 22 L80 15"),
                p("M30 196 L40 204 M90 196 L80 204"),
                r(19, 70, 7, 9, 2),
                r(94, 70, 7, 9, 2),
            ),
            wheels = four(36f, 150f, 14f, 94f),
        ),
        Kind.EV to Outline(
            body = listOf(p("M44 12 Q60 7 76 12 Q94 18 94 46 L94 182 Q94 204 78 208 Q60 212 42 208 Q26 204 26 182 L26 46 Q26 18 44 12 Z")),
            detail = listOf(
                p("M34 62 Q60 52 86 62 L84 172 Q60 180 36 172 Z"),
                p("M38 96 L82 96 M38 138 L82 138"),
                p("M32 20 Q60 12 88 20"),
                p("M32 200 Q60 207 88 200"),
                c(90, 186, 2.5),
                r(19, 68, 7, 9, 2),
                r(94, 68, 7, 9, 2),
            ),
            wheels = four(36f, 150f, 14f, 94f),
        ),
        Kind.SUV to Outline(
            body = listOf(r(24, 10, 72, 200, 14)),
            detail = listOf(
                p("M30 56 Q60 48 90 56 L86 78 Q60 73 34 78 Z"),
                r(36, 82, 48, 96, 5),
                p("M33 86 L33 174 M87 86 L87 174"),
                p("M36 186 L84 186 L82 200 L38 200 Z"),
                p("M32 18 L88 18 M30 24 L42 14 M90 24 L78 14"),
                r(17, 60, 7, 10, 2),
                r(96, 60, 7, 10, 2),
            ),
            wheels = four(30f, 150f, 11f, 97f, 13f, 30f),
        ),
        Kind.PICKUP to Outline(
            body = listOf(
                p("M38 10 Q60 6 82 10 Q96 14 96 34 L96 112 L24 112 L24 34 Q24 14 38 10 Z"),
                r(24, 116, 72, 94, 4),
            ),
            detail = listOf(
                p("M30 52 Q60 44 90 52 L86 72 Q60 67 34 72 Z"),
                r(36, 76, 48, 30, 4),
                r(31, 122, 58, 80, 2),
                p("M31 142 L89 142 M31 162 L89 162 M31 182 L89 182"),
                p("M32 16 L88 16"),
                r(17, 56, 7, 10, 2),
                r(96, 56, 7, 10, 2),
            ),
            wheels = four(26f, 160f, 11f, 97f, 13f, 30f),
        ),
        Kind.BOX_TRUCK to Outline(
            body = listOf(
                p("M36 8 Q60 4 84 8 Q92 10 92 22 L92 60 L28 60 L28 22 Q28 10 36 8 Z"),
                r(22, 64, 76, 150, 3),
            ),
            detail = listOf(
                p("M32 30 Q60 24 88 30 L86 46 Q60 42 34 46 Z"),
                p("M22 94 L98 94 M22 124 L98 124 M22 154 L98 154 M22 184 L98 184"),
                r(21, 30, 7, 10, 2),
                r(92, 30, 7, 10, 2),
            ),
            wheels = listOf(
                Wheel(16f, 16f, 12f, 26f, listOf("fl")),
                Wheel(92f, 16f, 12f, 26f, listOf("fr")),
                Wheel(10f, 152f, 12f, 26f, listOf("rl")),
                Wheel(98f, 152f, 12f, 26f, listOf("rr")),
                Wheel(10f, 182f, 12f, 26f),
                Wheel(98f, 182f, 12f, 26f),
            ),
        ),
        Kind.MOTORHOME to Outline(
            body = listOf(p("M34 8 Q60 3 86 8 Q98 12 98 30 L98 204 Q98 212 90 212 L30 212 Q22 212 22 204 L22 30 Q22 12 34 8 Z")),
            detail = listOf(
                p("M28 22 Q60 14 92 22 L90 38 Q60 33 30 38 Z"),
                r(44, 58, 32, 22, 3),
                r(46, 126, 28, 20, 3),
                r(50, 170, 20, 14, 2),
                p("M30 96 L90 96 M30 110 L90 110"),
                r(15, 26, 7, 10, 2),
                r(98, 26, 7, 10, 2),
            ),
            wheels = four(36f, 160f, 10f, 98f, 12f, 28f),
        ),
        Kind.TRAILER to Outline(
            body = listOf(
                p("M60 14 L36 54 M60 14 L84 54"),
                r(26, 54, 68, 152, 4),
            ),
            detail = listOf(
                c(60, 12, 5),
                p("M60 17 L60 30"),
                r(56, 30, 8, 10, 1),
                p("M32 60 L88 60 L88 200 L32 200 Z"),
                p("M26 128 L94 128"),
                p("M30 206 L30 212 M90 206 L90 212"),
            ),
            wheels = four(100f, 132f, 14f, 94f, 12f, 26f),
        ),
        Kind.UTV to Outline(
            body = listOf(p("M44 24 Q60 18 76 24 Q88 28 88 46 L88 180 Q88 194 74 196 L46 196 Q32 194 32 180 L32 46 Q32 28 44 24 Z")),
            detail = listOf(
                r(36, 70, 48, 74, 6),
                p("M36 90 L84 90 M36 124 L84 124"),
                r(40, 96, 17, 22, 4),
                r(63, 96, 17, 22, 4),
                r(38, 152, 44, 36, 2),
                p("M38 164 L82 164 M38 176 L82 176"),
                p("M40 34 L80 34"),
            ),
            wheels = four(32f, 150f, 16f, 88f, 16f, 30f).map { it.copy(knobby = true) },
        ),
        Kind.GOLF to Outline(
            body = listOf(p("M44 30 Q60 24 76 30 Q86 34 86 50 L86 184 Q86 196 74 196 L46 196 Q34 196 34 184 L34 50 Q34 34 44 30 Z")),
            detail = listOf(
                r(30, 68, 60, 84, 6, dashed = true),
                r(38, 104, 44, 18, 4),
                c(48, 86, 6),
                r(46, 162, 28, 28, 8),
                c(54, 176, 3),
                c(66, 176, 3),
            ),
            wheels = four(42f, 156f, 24f, 86f, 10f, 22f),
        ),
        Kind.MOTO to Outline(
            body = listOf(
                p("M52 52 Q60 46 68 52 L74 104 Q76 126 70 152 Q60 162 50 152 Q44 126 46 104 Z"),
                Shape.Path("M30 50 Q60 40 90 50", open = true),
            ),
            detail = listOf(
                Shape.Ellipse(60f, 84f, 11f, 18f),
                r(52, 108, 16, 36, 7),
                c(60, 56, 3),
                p("M26 48 L32 50 M94 48 L88 50"),
                p("M70 134 L80 168"),
            ),
            wheels = listOf(
                Wheel(54f, 12f, 12f, 38f, listOf("fl", "fr")),
                Wheel(53f, 166f, 14f, 40f, listOf("rl", "rr")),
            ),
        ),
        Kind.LOADER to Outline(
            body = listOf(
                p("M18 8 L102 8 L96 26 L24 26 Z"),
                r(32, 40, 56, 62, 6),
                r(30, 112, 60, 96, 8),
            ),
            detail = listOf(
                p("M40 26 L44 40 M80 26 L76 40"),
                c(60, 107, 5),
                r(40, 122, 40, 36, 4),
                p("M40 140 L80 140"),
                p("M36 192 Q60 204 84 192"),
                p("M44 172 L76 172 M44 180 L76 180"),
            ),
            wheels = four(46f, 140f, 12f, 92f, 18f, 40f).map { it.copy(knobby = true) },
        ),
        Kind.FORKLIFT to Outline(
            body = listOf(
                p("M44 4 L50 4 L50 46 L44 46 Z M70 4 L76 4 L76 46 L70 46 Z"),
                r(34, 46, 52, 10, 2),
                p("M34 62 L86 62 L86 182 Q86 204 60 206 Q34 204 34 182 Z"),
            ),
            detail = listOf(
                r(40, 76, 40, 64, 3),
                p("M40 98 L80 98 M40 120 L80 120"),
                c(54, 90, 5),
                r(46, 124, 28, 12, 3),
                p("M38 178 Q60 196 82 178"),
            ),
            wheels = listOf(
                Wheel(20f, 64f, 14f, 30f, listOf("fl")),
                Wheel(86f, 64f, 14f, 30f, listOf("fr")),
                Wheel(24f, 160f, 10f, 20f, listOf("rl")),
                Wheel(86f, 160f, 10f, 20f, listOf("rr")),
            ),
        ),
        Kind.MOWER to Outline(
            body = listOf(
                p("M46 26 Q60 20 74 26 Q82 30 82 44 L82 196 Q82 204 74 204 L46 204 Q38 204 38 196 L38 44 Q38 30 46 26 Z"),
                p("M20 96 L100 96 L100 132 Q100 138 94 138 L26 138 Q20 138 20 132 Z"),
            ),
            detail = listOf(
                c(60, 80, 8),
                p("M60 72 L60 88 M52 80 L68 80"),
                r(46, 150, 28, 30, 8),
                p("M30 104 L90 104 M30 130 L90 130"),
                p("M44 36 L76 36"),
            ),
            wheels = listOf(
                Wheel(26f, 32f, 10f, 20f, listOf("fl")),
                Wheel(84f, 32f, 10f, 20f, listOf("fr")),
                Wheel(20f, 156f, 16f, 34f, listOf("rl")),
                Wheel(84f, 156f, 16f, 34f, listOf("rr")),
            ),
        ),
        Kind.CRANE to Outline(
            body = listOf(r(26, 8, 68, 206, 6)),
            detail = listOf(
                r(30, 14, 22, 26, 3),
                p("M54 26 L66 26 L66 176 L54 176 Z"),
                p("M54 50 L66 62 M66 50 L54 62 M54 80 L66 92 M66 80 L54 92 M54 110 L66 122 M66 110 L54 122"),
                c(60, 158, 16),
                p("M26 72 L14 72 M94 72 L106 72 M26 192 L14 192 M94 192 L106 192"),
            ),
            wheels = four(22f, 180f, 14f, 94f, 12f, 22f) + listOf(
                Wheel(14f, 48f, 12f, 22f),
                Wheel(94f, 48f, 12f, 22f),
                Wheel(14f, 128f, 12f, 22f),
                Wheel(94f, 128f, 12f, 22f),
                Wheel(14f, 154f, 12f, 22f),
                Wheel(94f, 154f, 12f, 22f),
            ),
        ),
    )

    fun outline(kind: Kind): Outline = OUTLINES.getValue(kind)

    // ------------------------------------------------------------------
    // SVG path parsing (absolute M / L / Q / Z — all the web outlines use)
    // ------------------------------------------------------------------

    sealed interface PathCmd {
        data class MoveTo(val x: Float, val y: Float) : PathCmd
        data class LineTo(val x: Float, val y: Float) : PathCmd
        data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCmd
        data object Close : PathCmd
    }

    private val TOKEN = Regex("[MLQZmlqz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

    /** Parses absolute `M`/`L`/`Q`/`Z` path data; implicit repeats are honoured (`M` repeats as `L`). */
    fun parsePath(d: String): List<PathCmd> {
        val out = mutableListOf<PathCmd>()
        val tokens = TOKEN.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        fun num(): Float? = tokens.getOrNull(i)?.toFloatOrNull()?.also { i++ }
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t[0].uppercaseChar()
                i++
                if (cmd == 'Z') { out += PathCmd.Close; continue }
            } else if (cmd == 'Z') {
                i++ // stray number after Z — ignore
                continue
            }
            when (cmd) {
                'M' -> {
                    val x = num() ?: break; val y = num() ?: break
                    out += PathCmd.MoveTo(x, y); cmd = 'L'
                }
                'L' -> {
                    val x = num() ?: break; val y = num() ?: break
                    out += PathCmd.LineTo(x, y)
                }
                'Q' -> {
                    val cx = num() ?: break; val cy = num() ?: break
                    val x = num() ?: break; val y = num() ?: break
                    out += PathCmd.QuadTo(cx, cy, x, y)
                }
                else -> i++
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Zones (web `zoneRects`) and hit testing
    // ------------------------------------------------------------------

    data class ZoneRect(val zone: String, val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float = 3f) {
        val cx: Float get() = x + w / 2f
        val cy: Float get() = y + h / 2f
        fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
    }

    /** Hit areas painted back-to-front: cab/bed, edges, then wheels on top (web order). */
    fun zones(kind: Kind): List<ZoneRect> = buildList {
        if (hasBed(kind)) {
            add(ZoneRect("cab", 30f, 34f, 60f, 78f))
            add(ZoneRect("bed", 30f, 112f, 60f, 74f))
        } else {
            add(ZoneRect("cab", 30f, 34f, 60f, 152f))
        }
        add(ZoneRect("front", 26f, 0f, 68f, 34f, 6f))
        add(ZoneRect("rear", 26f, 186f, 68f, 34f, 6f))
        add(ZoneRect("left", 0f, 34f, 30f, 152f))
        add(ZoneRect("right", 90f, 34f, 30f, 152f))
        val seen = mutableSetOf<String>()
        outline(kind).wheels.forEach { w ->
            val c = w.corners.firstOrNull() ?: return@forEach
            if (!seen.add(c)) return@forEach
            add(ZoneRect(c, w.x - 4f, w.y - 4f, w.w + 8f, w.h + 8f, 4f))
        }
    }

    /** Zone under a viewBox point; the topmost (last painted) zone wins, like the SVG. */
    fun hitZone(zones: List<ZoneRect>, x: Float, y: Float): String? = zones.lastOrNull { it.contains(x, y) }?.zone

    /** Zones in reading order for accessibility (front → wheels → sides → body → rear). */
    fun accessibilityOrder(zones: List<ZoneRect>): List<ZoneRect> {
        val order = listOf("front", "fl", "fr", "left", "cab", "bed", "right", "rl", "rr", "rear")
        return zones.sortedBy { order.indexOf(it.zone).let { i -> if (i < 0) order.size else i } }
    }

    // ------------------------------------------------------------------
    // Marks (pins)
    // ------------------------------------------------------------------

    /**
     * One pin on the sketch. [id] is the inspection item id (stable across clients — the contract
     * has no separate mark id); [number] is the 1-based key number shown inside the pin.
     */
    data class SketchMark(
        val id: String,
        val zone: String,
        val severity: String,
        val label: String,
        val number: Int,
        /** Rental check-in: not present in the baseline (check-out) set. */
        val isNew: Boolean = false,
    )

    private fun sevRank(severity: String) = when (severity) {
        InspectionV2.SEV_OUT_OF_SERVICE -> 0
        InspectionV2.SEV_MAJOR -> 1
        else -> 2
    }

    /** Worse of two severities (out_of_service > major > minor). */
    fun worse(a: String, b: String): String = if (sevRank(b) < sevRank(a)) b else a

    /**
     * Marks for envelope / live defects that carry a valid location: one per item (corner defects
     * of one item share its `__loc`), worst severity wins, numbered in defect order.
     */
    fun marksFromDefects(
        defects: List<InspectionV2.EnvelopeDefect>,
        labelOf: (InspectionV2.EnvelopeDefect) -> String = { it.label },
    ): List<SketchMark> {
        val byItem = LinkedHashMap<String, SketchMark>()
        defects.forEach { d ->
            val loc = d.location?.takeIf { it in InspectionV2.LOCATIONS } ?: return@forEach
            val prev = byItem[d.itemId]
            byItem[d.itemId] = if (prev == null) {
                SketchMark(d.itemId, loc, d.severity, labelOf(d), 0)
            } else {
                prev.copy(severity = worse(prev.severity, d.severity))
            }
        }
        return byItem.values.mapIndexed { i, m -> m.copy(number = i + 1) }
    }

    /** Flags marks absent from [baseline] (same item id and zone) as new — rental check-in. */
    fun flagNew(current: List<SketchMark>, baseline: List<SketchMark>): List<SketchMark> {
        val base = baseline.map { it.id to it.zone }.toSet()
        return current.map { it.copy(isNew = (it.id to it.zone) !in base) }
    }

    data class PlacedMark(val mark: SketchMark, val x: Float, val y: Float, val baseline: Boolean)

    data class Layout(
        val placed: List<PlacedMark>,
        /** Marks whose zone is not drawn for this kind (roof / under / … or a missing wheel). */
        val offSketch: List<SketchMark>,
        val offSketchBaseline: List<SketchMark>,
    )

    /**
     * Deterministic pin positions: marks sharing a zone are spread along the zone's longer axis
     * ([PIN_STEP] apart, centred), clamped inside the canvas. Baseline pins come first in a zone.
     */
    fun layout(kind: Kind, marks: List<SketchMark>, baseline: List<SketchMark> = emptyList()): Layout {
        val zoneMap = zones(kind).associateBy { it.zone }
        val placed = mutableListOf<PlacedMark>()
        val all = baseline.map { it to true } + marks.map { it to false }
        all.filter { it.first.zone in zoneMap }
            .groupBy { it.first.zone }
            .forEach { (zone, group) ->
                val z = zoneMap.getValue(zone)
                val n = group.size
                val vertical = z.h >= z.w
                group.forEachIndexed { i, (m, isBase) ->
                    val off = (i - (n - 1) / 2f) * PIN_STEP
                    val x = if (vertical) z.cx else z.cx + off
                    val y = if (vertical) z.cy + off else z.cy
                    placed += PlacedMark(m, clamp(x, PIN_R, VIEW_W - PIN_R), clamp(y, PIN_R, VIEW_H - PIN_R), isBase)
                }
            }
        return Layout(
            placed = placed,
            offSketch = marks.filter { it.zone !in zoneMap },
            offSketchBaseline = baseline.filter { it.zone !in zoneMap },
        )
    }

    /** Pin under a viewBox point within [radius] (current marks win over baseline, nearest first). */
    fun hitMark(placed: List<PlacedMark>, x: Float, y: Float, radius: Float, includeBaseline: Boolean = false): PlacedMark? =
        placed.filter { includeBaseline || !it.baseline }
            .map { it to ((it.x - x) * (it.x - x) + (it.y - y) * (it.y - y)) }
            .filter { it.second <= radius * radius }
            .sortedWith(compareBy<Pair<PlacedMark, Float>> { it.first.baseline }.thenBy { it.second })
            .firstOrNull()?.first

    /** The single values write for moving / placing an item's mark: `<itemId>__loc = zone`. */
    fun moveValue(itemId: String, zone: String): Pair<String, String> = InspectionV2.locKey(itemId) to zone

    /** The values write for removing a mark (web clears the key to ""). */
    fun removeValue(itemId: String): Pair<String, String> = InspectionV2.locKey(itemId) to ""

    /** Maps a canvas point (px) to viewBox units given the fitted scale and offset. */
    fun toView(px: Float, py: Float, scale: Float, offsetX: Float, offsetY: Float): Pair<Float, Float> =
        (px - offsetX) / scale to (py - offsetY) / scale

    /** Uniform scale that fits the viewBox into a w×h box. */
    fun fitScale(w: Float, h: Float): Float = min(w / VIEW_W, h / VIEW_H)

    private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
}
