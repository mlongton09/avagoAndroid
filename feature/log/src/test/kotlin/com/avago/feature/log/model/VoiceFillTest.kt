package com.avago.feature.log.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mirrors avagoweb src/lib/log/voiceFill.test.ts. */
class VoiceFillTest {

    private val opts = listOf("insp.opt.normal", "insp.opt.monitor", "insp.opt.needs_repair", "insp.opt.na")
        .map { InspectionOption(value = it, rawValue = it) }

    private fun checklist(dict: Map<String, String>) = InspectionChecklist(
        title = "t",
        groups = listOf(
            InspectionGroup(
                "g", "g",
                listOf(
                    InspectionSection(
                        "s", "s",
                        listOf(
                            item("brakes", dict["insp.brakes"]!!, "insp.brakes"),
                            item("horn", dict["insp.horn"]!!, "insp.horn"),
                            item("wipers", "Windshield wipers", "Windshield wipers"),
                            item("lights", dict["insp.lights"]!!, "insp.lights", "corner-select").copy(
                                corners = listOf(
                                    InspectionCorner("l_fl", dict["insp.corner.fl"]!!, "insp.corner.fl"),
                                    InspectionCorner("l_rr", dict["insp.corner.rr"]!!, "insp.corner.rr"),
                                ),
                            ),
                            InspectionItem(id = "tp", text = dict["insp.tp"]!!, rawText = "insp.tp", type = "wheel-data", unit = "PSI"),
                            InspectionItem(id = "oil", text = "Oil level", type = "measurement"),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun item(id: String, text: String, raw: String, type: String = "select") =
        InspectionItem(id = id, text = text, rawText = raw, type = type, options = opts)

    private val en = mapOf(
        "insp.brakes" to "Brakes", "insp.horn" to "Horn", "insp.lights" to "Headlights",
        "insp.corner.fl" to "left front", "insp.corner.rr" to "right rear", "insp.tp" to "Tire pressure",
    )
    private val de = mapOf(
        "insp.brakes" to "Bremsen", "insp.horn" to "Hupe", "insp.lights" to "Scheinwerfer",
        "insp.corner.fl" to "vorne links", "insp.corner.rr" to "hinten rechts", "insp.tp" to "Reifendruck",
    )
    private val cornersEn = mapOf("fl" to "left front", "fr" to "right front", "rl" to "left rear", "rr" to "right rear")
    private val cornersDe = mapOf("fl" to "vorne links", "fr" to "vorne rechts", "rl" to "hinten links", "rr" to "hinten rechts")

    private fun targets(dict: Map<String, String>, c: Map<String, String>) =
        VoiceFill.buildTargets(checklist(dict)) { c.getValue(it) }

    @Test
    fun `tokenizes and splits clauses`() {
        assertEquals(listOf("tire", "pressure", "32.5", "psi"), VoiceFill.tokenize("Tire pressure 32,5 PSI"))
        assertEquals(listOf("entfallt"), VoiceFill.tokenize("Entfällt"))
        assertEquals(listOf("brakes good", "horn bad", "wipers ok"), VoiceFill.splitClauses("brakes good, horn bad and wipers ok"))
    }

    @Test
    fun `parses an English walk-through`() {
        val r = VoiceFill.parse(
            "Brakes good, horn needs repair note button stuck. Wipers worn, headlights left front not applicable, " +
                "tire pressure 32, oil level 80, flux capacitor fine",
            targets(en, cornersEn),
        )
        fun by(k: String, kind: VoiceFill.ChangeKind = VoiceFill.ChangeKind.ANSWER) =
            r.changes.filter { it.key == k && it.kind == kind }
        assertEquals("insp.opt.normal", by("brakes")[0].value)
        assertEquals("insp.opt.needs_repair", by("horn")[0].value)
        assertEquals("button stuck", by("horn__note", VoiceFill.ChangeKind.NOTE)[0].value)
        assertEquals("insp.opt.monitor", by("wipers")[0].value)
        assertEquals("worn", by("wipers__note", VoiceFill.ChangeKind.NOTE)[0].value)
        assertEquals("insp.opt.na", by("l_fl")[0].value)
        assertTrue(by("l_rr").isEmpty())
        assertEquals(listOf("fl", "fr", "rl", "rr"), by("tp", VoiceFill.ChangeKind.NUMBER).map { it.corner })
        assertEquals("80", by("oil", VoiceFill.ChangeKind.NUMBER)[0].value)
        assertEquals(listOf("flux capacitor fine"), r.unmatched)
    }

    @Test
    fun `parses a German walk-through with decimal comma`() {
        val r = VoiceFill.parse(
            "Bremsen in Ordnung, Hupe defekt Notiz klemmt, Scheinwerfer hinten rechts beobachten und " +
                "Reifendruck vorne links 2,4 dann Hupe entfällt",
            targets(de, cornersDe),
        )
        fun v(k: String, kind: VoiceFill.ChangeKind = VoiceFill.ChangeKind.ANSWER) =
            r.changes.firstOrNull { it.key == k && it.kind == kind }?.value
        assertEquals("insp.opt.normal", v("brakes"))
        assertEquals("insp.opt.na", v("horn"))
        assertEquals("klemmt", v("horn__note", VoiceFill.ChangeKind.NOTE))
        assertEquals("insp.opt.monitor", v("l_rr"))
        val tp = r.changes.filter { it.key == "tp" }
        assertEquals(1, tp.size)
        assertEquals("fl", tp[0].corner)
        assertEquals("2.4", tp[0].value)
        assertTrue(r.unmatched.isEmpty())
    }

    @Test
    fun `builds writes merging wheel data and notes`() {
        val r = VoiceFill.parse("tire pressure right rear 35, horn bad note loose wire", targets(en, cornersEn))
        val w = VoiceFill.writes(r.changes, mapOf("tp" to """{"fl":"30"}""", "horn__note" to "Old")).toMap()
        assertEquals(mapOf("fl" to "30", "rr" to "35"), InspectionV2.wheelDict(w["tp"]))
        assertEquals("insp.opt.needs_repair", w["horn"])
        assertEquals("Old. Loose wire", w["horn__note"])
    }

    @Test
    fun `reports a note with no item as unmatched`() {
        assertEquals(listOf("note something"), VoiceFill.parse("note something", targets(en, cornersEn)).unmatched)
    }
}
