package com.avago.feature.log.model

import com.avago.feature.log.model.InspectionValidation.MeterIssue
import com.avago.feature.log.model.InspectionValidation.MeterPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Mirrors avagoweb bulkActions/inspectionProgress/meterSanity/quickPhrases tests. */
class InspectionValidationTest {

    private fun checklist(json: String) = parseInspectionChecklist(json, emptyMap())!!

    private val notes = checklist(
        """
        {"groups":[{"id":"g","sections":[{"id":"s","items":[
          {"id":"a","options":["insp.opt.normal","insp.opt.monitor","insp.opt.needs_repair"]},
          {"id":"b","options":["insp.opt.normal","insp.opt.monitor","insp.opt.needs_repair"]},
          {"id":"tires","type":"corner-select","options":["insp.opt.normal","insp.opt.needs_repair"],"corners":[{"id":"t_fl","label":"FL"}]},
          {"id":"psi","type":"measurement","min":28,"max":45}
        ]}]}]}
        """.trimIndent(),
    )

    private val ranged = checklist(
        """
        {"groups":[{"id":"g","sections":[{"id":"s","items":[
          {"id":"pads","options":["Normal","Monitor","Needs Repair"],"required":true},
          {"id":"tread","type":"measurement","min":3,"max":12,"required":true},
          {"id":"press","type":"number-input","inputs":[{"id":"p_f","label":"Front","min":30},{"id":"p_r","label":"Rear"}]},
          {"id":"wheel","type":"wheel-data","max":10,"required":true}
        ]}]}]}
        """.trimIndent(),
    )

    @Test
    fun `requires notes for NR and out-of-service answers`() {
        val v = mapOf(
            "a" to "insp.opt.needs_repair",
            "b" to "insp.opt.monitor", "b__sev" to "out_of_service",
            "t_fl" to "insp.opt.needs_repair",
            "psi" to "10", "psi__sev" to "out_of_service",
        )
        assertEquals(listOf("a", "b", "t_fl", "psi"), InspectionValidation.missingDefectNotes(notes, v).map { it.key })
        val noted = v + mapOf("a__note" to "leak", "tires__note" to "flat")
        assertEquals(listOf("b", "psi"), InspectionValidation.missingDefectNotes(notes, noted).map { it.key })
    }

    @Test
    fun `does not require notes for Monitor or in-range readings`() {
        val v = mapOf("b" to "insp.opt.monitor", "psi" to "30", "psi__sev" to "out_of_service")
        assertTrue(InspectionValidation.missingDefectNotes(notes, v).isEmpty())
    }

    @Test
    fun `lists missing required items in form order including wheel corners`() {
        assertEquals(listOf("pads", "tread", "wheel"), InspectionValidation.missingRequired(ranged, emptyMap()).map { it.key })
        val partial = mapOf("pads" to "Normal", "tread" to "5", "wheel" to """{"fl":"1","fr":"1","rl":"1"}""")
        assertEquals(listOf("wheel"), InspectionValidation.missingRequired(ranged, partial).map { it.key })
        val full = partial + ("wheel" to """{"fl":"1","fr":"1","rl":"1","rr":"2"}""")
        assertTrue(InspectionValidation.missingRequired(ranged, full).isEmpty())
    }

    @Test
    fun `range severity is major beyond 25 percent`() {
        assertNull(InspectionValidation.rangeSeverity("5", 3.0, 12.0))
        assertEquals("minor", InspectionValidation.rangeSeverity("2.5", 3.0, 12.0))
        assertEquals("major", InspectionValidation.rangeSeverity("2", 3.0, 12.0))
        assertEquals("major", InspectionValidation.rangeSeverity("16", 3.0, 12.0))
        assertEquals("minor", InspectionValidation.rangeSeverity("-0.2", 0.0, null))
    }

    @Test
    fun `numeric writes keep auto severity but never override out of service`() {
        val item = ranged.allItems().first { it.id == "tread" }
        assertEquals(listOf("tread" to "2", "tread__sev" to "major"), InspectionValidation.measureWrites(item, emptyMap(), listOf("tread" to "2")))
        assertEquals(
            listOf("tread" to "5", "tread__sev" to ""),
            InspectionValidation.measureWrites(item, mapOf("tread__sev" to "major"), listOf("tread" to "5")),
        )
        assertEquals(
            listOf("tread" to "1"),
            InspectionValidation.measureWrites(item, mapOf("tread__sev" to "out_of_service"), listOf("tread" to "1")),
        )
    }

    @Test
    fun `wheel corner writes merge into the json dict`() {
        val slot = InspectionValidation.measureSlots(ranged.allItems().first { it.id == "wheel" })[1]
        val (k, v) = InspectionValidation.slotWrite(mapOf("wheel" to """{"fl":"3"}"""), slot, "11")
        assertEquals("wheel", k)
        assertEquals(mapOf("fl" to "3", "fr" to "11"), InspectionV2.wheelDict(v))
        assertEquals("", InspectionValidation.slotWrite(mapOf("wheel" to """{"fr":"3"}"""), slot, "").second)
    }

    @Test
    fun `section progress counts score targets and attention`() {
        val p = InspectionValidation.sectionProgress(ranged, mapOf("pads" to "Normal")).single()
        assertEquals(1, p.answered)
        assertEquals(1, p.total)
        assertEquals(2, p.attention)
    }

    private fun day(s: String) = LocalDate.parse(s).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun endOfDay(s: String) = LocalDate.parse(s).atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val points = listOf(
        MeterPoint(10000.0, day("2026-01-01"), "a"),
        MeterPoint(10500.0, java.time.Instant.parse("2026-01-05T08:00:00Z").toEpochMilli()),
        MeterPoint(11000.0, day("2026-01-11"), "b"),
        MeterPoint(20000.0, day("2026-03-01"), "c"),
    )

    private fun check(entered: Double?, at: String, kind: String = "odometer", pts: List<MeterPoint> = points, exclude: String? = null) =
        InspectionValidation.checkMeterSanity(entered, day(at), endOfDay(at), pts, kind, exclude)

    @Test
    fun `meter parsing and sanity`() {
        assertEquals(12345.0, InspectionValidation.parseMeterValue("12,345"))
        assertEquals(1234.5, InspectionValidation.parseMeterValue("1 234.5 mi"))
        assertNull(InspectionValidation.parseMeterValue(""))
        assertNull(InspectionValidation.parseMeterValue("abc"))

        val lower = check(10900.0, "2026-01-20")
        assertEquals(MeterIssue.LOWER, lower?.type)
        assertEquals(11000.0, lower?.last?.value)
        assertNull(check(10600.0, "2026-01-06"))
        assertEquals(MeterIssue.RATE, check(30000.0, "2026-01-12")?.type)
        assertNull(check(12000.0, "2026-01-20"))
        val hrs = listOf(MeterPoint(100.0, day("2026-01-31")))
        assertNull(check(120.0, "2026-02-01", "engine_hours", hrs))
        assertEquals(MeterIssue.RATE, check(130.0, "2026-02-01", "engine_hours", hrs)?.type)
        assertNull(check(19000.0, "2026-03-01", exclude = "c"))
        assertNull(check(5.0, "2026-01-01", pts = emptyList()))
        assertNull(check(null, "2026-01-01"))
    }

    @Test
    fun `quick phrases append like web`() {
        assertEquals("Worn", InspectionValidation.appendPhrase("", "worn"))
        assertEquals("Worn, cracked", InspectionValidation.appendPhrase("Worn", "Cracked"))
        assertEquals("Worn, cracked", InspectionValidation.appendPhrase("Worn, cracked", "worn"))
        assertEquals("Seal failed. leaking", InspectionValidation.appendPhrase("Seal failed.", "leaking"))
        assertEquals("x", InspectionValidation.appendPhrase("x", "  "))
    }

    @Test
    fun `sketch hit test prefers wheels and splits cab and bed`() {
        assertTrue(VehicleSketchModel.hasBed("Commercial Vehicle"))
        assertFalse(VehicleSketchModel.hasBed("car"))
        val bed = VehicleSketchModel.zones(VehicleSketchModel.Kind.PICKUP)
        assertEquals("fl", VehicleSketchModel.hitZone(bed, 15f, 40f))
        assertEquals("left", VehicleSketchModel.hitZone(bed, 5f, 100f))
        assertEquals("bed", VehicleSketchModel.hitZone(bed, 60f, 150f))
        assertEquals("cab", VehicleSketchModel.hitZone(VehicleSketchModel.zones(VehicleSketchModel.Kind.SEDAN), 60f, 150f))
        assertEquals("front", VehicleSketchModel.hitZone(bed, 60f, 10f))
    }
}
