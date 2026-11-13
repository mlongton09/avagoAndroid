package com.avago.feature.log.model

import com.avago.feature.log.model.VehicleSketchModel.Kind
import com.avago.feature.log.model.VehicleSketchModel.PathCmd
import com.avago.feature.log.model.VehicleSketchModel.SketchMark
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Vehicle sketch / damage map: geometry parity with web `VehicleSketch.tsx`, pin layout, and the
 * zone-based data contract (`<itemId>__loc` + envelope `defects[].location`) round trip.
 */
class VehicleSketchModelTest {

    private val checklist = parseInspectionChecklist(
        """
        {"groups":[{"id":"g","sections":[
          {"id":"s1","title":"Body","items":[
            {"id":"door","text":"Door","options":["Normal","Monitor","Needs Repair"]},
            {"id":"bumper","text":"Bumper","options":["Normal","Monitor","Needs Repair"]},
            {"id":"mirror","text":"Mirror","options":["Normal","Monitor","Needs Repair"]},
            {"id":"roofpanel","text":"Roof","options":["Normal","Monitor","Needs Repair"]}
          ]},
          {"id":"s2","title":"Tires","items":[
            {"id":"tires","text":"Tires","type":"corner-select","options":["Normal","Monitor","Needs Repair"],
             "corners":[{"id":"t_fl","label":"FL"},{"id":"t_fr","label":"FR"}]}
          ]}
        ]}]}
        """.trimIndent(),
        emptyMap(),
    )!!

    // -- kind mapping (web sketchKindFor) ---------------------------------

    @Test
    fun `kind mapping accepts web and iOS asset type ids`() {
        assertEquals(Kind.SUV, VehicleSketchModel.kindFor("light_vehicle"))
        assertEquals(Kind.SUV, VehicleSketchModel.kindFor("LightVehicle"))
        assertEquals(Kind.BOX_TRUCK, VehicleSketchModel.kindFor("Commercial Vehicle"))
        assertEquals(Kind.UTV, VehicleSketchModel.kindFor("ATV/UTV"))
        assertEquals(Kind.TRAILER, VehicleSketchModel.kindFor("generator"))
        assertEquals(Kind.LOADER, VehicleSketchModel.kindFor("HeavyEquipment"))
        assertEquals(Kind.MOWER, VehicleSketchModel.kindFor("lawn_equipment"))
        assertEquals(Kind.SEDAN, VehicleSketchModel.kindFor(null))
        assertEquals(Kind.SEDAN, VehicleSketchModel.kindFor("spaceship"))
        assertTrue(VehicleSketchModel.hasBed("truck"))
        assertFalse(VehicleSketchModel.hasBed("car"))
    }

    // -- geometry ------------------------------------------------------------

    @Test
    fun `every kind has an outline with parseable paths inside the viewBox`() {
        Kind.entries.forEach { kind ->
            val o = VehicleSketchModel.outline(kind)
            assertTrue(o.body.isNotEmpty(), "$kind body")
            (o.body + o.detail).filterIsInstance<VehicleSketchModel.Shape.Path>().forEach { p ->
                val cmds = VehicleSketchModel.parsePath(p.d)
                assertTrue(cmds.first() is PathCmd.MoveTo, "$kind ${p.d}")
                cmds.forEach { c ->
                    val pts = when (c) {
                        is PathCmd.MoveTo -> listOf(c.x to c.y)
                        is PathCmd.LineTo -> listOf(c.x to c.y)
                        is PathCmd.QuadTo -> listOf(c.cx to c.cy, c.x to c.y)
                        PathCmd.Close -> emptyList()
                    }
                    pts.forEach { (x, y) ->
                        assertTrue(x in 0f..VehicleSketchModel.VIEW_W && y in 0f..VehicleSketchModel.VIEW_H, "$kind $x,$y")
                    }
                }
            }
        }
    }

    @Test
    fun `path parser handles subpaths and quads`() {
        val cmds = VehicleSketchModel.parsePath("M44 22 Q60 17 76 22 M30 22 L40 15 Z")
        assertEquals(
            listOf(
                PathCmd.MoveTo(44f, 22f),
                PathCmd.QuadTo(60f, 17f, 76f, 22f),
                PathCmd.MoveTo(30f, 22f),
                PathCmd.LineTo(40f, 15f),
                PathCmd.Close,
            ),
            cmds,
        )
        // Implicit lineto after moveto, decimals and commas.
        assertEquals(
            listOf(PathCmd.MoveTo(1.5f, 2f), PathCmd.LineTo(3f, 4f)),
            VehicleSketchModel.parsePath("M1.5,2 3,4"),
        )
    }

    @Test
    fun `zones match web zoneRects`() {
        val sedan = VehicleSketchModel.zones(Kind.SEDAN)
        assertEquals(listOf("cab", "front", "rear", "left", "right", "fl", "fr", "rl", "rr"), sedan.map { it.zone })
        val cab = sedan.first { it.zone == "cab" }
        assertEquals(listOf(30f, 34f, 60f, 152f), listOf(cab.x, cab.y, cab.w, cab.h))
        // Sedan front-left wheel {x:14, y:36, w:12, h:26} -> (10, 32, 20, 34).
        val fl = sedan.first { it.zone == "fl" }
        assertEquals(listOf(10f, 32f, 20f, 34f, 4f), listOf(fl.x, fl.y, fl.w, fl.h, fl.rx))

        val pickup = VehicleSketchModel.zones(Kind.PICKUP)
        assertEquals(listOf("cab", "bed"), pickup.take(2).map { it.zone })
        assertEquals(112f, pickup[1].y)

        // Motorcycle: one wheel per axle, tagged with its first corner only.
        assertEquals(listOf("fl", "rl"), VehicleSketchModel.zones(Kind.MOTO).map { it.zone }.filter { it in VehicleSketchModel.WHEEL_CORNERS })
        // Box truck: decorative rear axle adds no zone.
        assertEquals(4, VehicleSketchModel.zones(Kind.BOX_TRUCK).count { it.zone in VehicleSketchModel.WHEEL_CORNERS })
    }

    @Test
    fun `hit test uses paint order so wheels win over sides`() {
        val z = VehicleSketchModel.zones(Kind.SEDAN)
        assertEquals("fl", VehicleSketchModel.hitZone(z, 15f, 40f))
        assertEquals("left", VehicleSketchModel.hitZone(z, 5f, 100f))
        assertEquals("cab", VehicleSketchModel.hitZone(z, 60f, 120f))
        assertEquals("rear", VehicleSketchModel.hitZone(z, 60f, 210f))
        assertEquals("rr", VehicleSketchModel.hitZone(z, 100f, 160f))
        assertNull(VehicleSketchModel.hitZone(z, 2f, 2f))
    }

    @Test
    fun `knobby wheels get web tread lines`() {
        val w = VehicleSketchModel.outline(Kind.UTV).wheels.first()
        assertEquals(5, w.treads().size) // floor(30 / 6)
        assertEquals(Triple(18f, 36f, 30f), w.treads().first())
        assertTrue(VehicleSketchModel.outline(Kind.SEDAN).wheels.first().treads().isEmpty())
        assertEquals(4f, VehicleSketchModel.outline(Kind.LOADER).wheels.first().rx) // min(4, 18/3)
        assertEquals(10f / 3f, VehicleSketchModel.outline(Kind.GOLF).wheels.first().rx)
    }

    // -- marks ---------------------------------------------------------------

    private val values = mapOf(
        "door" to "Needs Repair", "door__loc" to "left", "door__sev" to "out_of_service",
        "bumper" to "Monitor", "bumper__loc" to "rear",
        "mirror" to "Needs Repair", "mirror__loc" to "left",
        "roofpanel" to "Monitor", "roofpanel__loc" to "roof",
        "t_fl" to "Monitor", "t_fr" to "Needs Repair", "tires__loc" to "fl",
    )

    @Test
    fun `marks come from located defects, one per item with worst severity`() {
        val defects = InspectionV2.collectDefects(checklist, values)
        val marks = VehicleSketchModel.marksFromDefects(defects)
        assertEquals(listOf("door", "mirror", "tires", "bumper", "roofpanel"), marks.map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 5), marks.map { it.number })
        val tires = marks.first { it.id == "tires" }
        assertEquals("fl", tires.zone)
        assertEquals(InspectionV2.SEV_MAJOR, tires.severity) // NR corner beats the M corner
        assertEquals(InspectionV2.SEV_OUT_OF_SERVICE, marks.first().severity)
        // Unknown / empty locations are not drawn.
        val none = VehicleSketchModel.marksFromDefects(
            InspectionV2.collectDefects(checklist, mapOf("door" to "Needs Repair", "door__loc" to "spoiler")),
        )
        assertTrue(none.isEmpty())
    }

    @Test
    fun `layout spreads pins in a zone and lists off-sketch locations`() {
        val marks = VehicleSketchModel.marksFromDefects(InspectionV2.collectDefects(checklist, values))
        val layout = VehicleSketchModel.layout(Kind.SEDAN, marks)
        val left = layout.placed.filter { it.mark.zone == "left" }
        assertEquals(2, left.size)
        assertEquals(15f, left[0].x)
        assertEquals(VehicleSketchModel.PIN_STEP, left[1].y - left[0].y)
        assertEquals(110f, (left[0].y + left[1].y) / 2f) // centred on the zone (34 + 152/2)
        assertEquals(listOf("roofpanel"), layout.offSketch.map { it.id })
        layout.placed.forEach {
            assertTrue(it.x >= VehicleSketchModel.PIN_R && it.x <= VehicleSketchModel.VIEW_W - VehicleSketchModel.PIN_R)
            assertTrue(it.y >= VehicleSketchModel.PIN_R && it.y <= VehicleSketchModel.VIEW_H - VehicleSketchModel.PIN_R)
        }
        // Moto has no front-right wheel zone -> off-sketch, never dropped.
        val fr = listOf(SketchMark("x", "fr", "minor", "X", 1))
        assertEquals(listOf("x"), VehicleSketchModel.layout(Kind.MOTO, fr).offSketch.map { it.id })
    }

    @Test
    fun `hit mark finds the nearest current pin, never baseline by default`() {
        val base = listOf(SketchMark("door", "left", "minor", "Door", 1))
        val cur = listOf(SketchMark("door", "left", "major", "Door", 1))
        val layout = VehicleSketchModel.layout(Kind.SEDAN, cur, base)
        val p = layout.placed.first { !it.baseline }
        assertEquals(p, VehicleSketchModel.hitMark(layout.placed, p.x, p.y, 6f))
        val b = layout.placed.first { it.baseline }
        assertNull(VehicleSketchModel.hitMark(layout.placed.filter { it.baseline }, b.x, b.y, 6f))
        assertEquals(b, VehicleSketchModel.hitMark(layout.placed.filter { it.baseline }, b.x, b.y, 6f, includeBaseline = true))
    }

    @Test
    fun `baseline comparison flags only marks absent from check-out`() {
        val baseline = listOf(
            SketchMark("door", "left", "minor", "Door", 1),
            SketchMark("bumper", "front", "minor", "Bumper", 2),
        )
        val current = listOf(
            SketchMark("door", "left", "major", "Door", 1), // same item + zone -> pre-existing
            SketchMark("bumper", "rear", "minor", "Bumper", 2), // moved zone -> new
            SketchMark("mirror", "right", "minor", "Mirror", 3), // new item
        )
        assertEquals(listOf(false, true, true), VehicleSketchModel.flagNew(current, baseline).map { it.isNew })
        val layout = VehicleSketchModel.layout(Kind.SEDAN, current, baseline)
        assertEquals(2, layout.placed.count { it.baseline })
        // Baseline + current share the left zone without overlapping.
        val left = layout.placed.filter { it.mark.zone == "left" }
        assertEquals(2, left.map { it.y }.distinct().size)
    }

    // -- data contract round trip ---------------------------------------------

    @Test
    fun `moving a pin writes only the web loc key and survives the envelope round trip`() {
        val (key, zone) = VehicleSketchModel.moveValue("door", "right")
        assertEquals("door__loc", key)
        val edited = values + (key to zone)
        val env = InspectionV2.buildEnvelope(
            stored = null, values = edited, subtype = "Base", mode = "Full", configSnapshot = null,
            defects = InspectionV2.collectDefects(checklist, edited), result = null, outOfService = false,
        )
        // Wire format: flat values map + defects[].location — nothing else.
        assertEquals("right", env["values"]!!.jsonObject["door__loc"]!!.jsonPrimitive.content)
        val doorDefect = (env["defects"] as JsonArray).map { it.jsonObject }.first { it["itemId"]!!.jsonPrimitive.content == "door" }
        assertEquals("right", doorDefect["location"]!!.jsonPrimitive.content)
        assertEquals(
            setOf("itemId", "key", "label", "cornerLabel", "sectionTitle", "answer", "bucket", "severity", "note", "location"),
            doorDefect.keys,
        )
        assertFalse(env["values"]!!.jsonObject.keys.any { it.endsWith("__x") || it.endsWith("__y") || it.contains("sketch") })

        // Re-read (another client opening the log) -> identical marks.
        val reparsed = InspectionV2.parseEnvelope(Json.parseToJsonElement(env.toString()) as JsonObject)
        val before = VehicleSketchModel.marksFromDefects(InspectionV2.collectDefects(checklist, edited))
        val after = VehicleSketchModel.marksFromDefects(reparsed.defects)
        assertEquals(before.map { Triple(it.id, it.zone, it.severity) }, after.map { Triple(it.id, it.zone, it.severity) })
        assertEquals(after, VehicleSketchModel.marksFromDefects(InspectionV2.collectDefects(checklist, reparsed.values)))
    }

    @Test
    fun `removing a mark clears the loc key like web`() {
        val (key, v) = VehicleSketchModel.removeValue("door")
        assertEquals("door__loc" to "", key to v)
        val edited = values + (key to v)
        val marks = VehicleSketchModel.marksFromDefects(InspectionV2.collectDefects(checklist, edited))
        assertFalse(marks.any { it.id == "door" })
    }

    @Test
    fun `web written fixture renders its pins`() {
        val stored = Json.parseToJsonElement(
            javaClass.classLoader!!.getResource("fixtures/web_inspection_v2.json")!!.readText(),
        ).jsonObject
        val parsed = InspectionV2.parseEnvelope(stored)
        val marks = VehicleSketchModel.marksFromDefects(parsed.defects)
        assertEquals(listOf(SketchMark("pads", "fl", "out_of_service", "Brake pads", 1)), marks)
        val layout = VehicleSketchModel.layout(VehicleSketchModel.kindFor("car"), marks)
        assertEquals(20f, layout.placed.single().x) // centre of the fl wheel zone (10 + 20/2)
        assertEquals(49f, layout.placed.single().y) // 32 + 34/2
    }

    @Test
    fun `view mapping inverts the fitted transform`() {
        val s = VehicleSketchModel.fitScale(240f, 600f)
        assertEquals(2f, s)
        val (x, y) = VehicleSketchModel.toView(40f + 10f * s, 80f + 20f * s, s, 40f, 80f)
        assertEquals(10f, x)
        assertEquals(20f, y)
    }
}
