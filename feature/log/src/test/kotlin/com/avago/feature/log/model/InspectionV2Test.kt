package com.avago.feature.log.model

import com.avago.feature.log.model.InspectionV2.Bucket
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mirrors avagoweb's openDefects.test.ts and inspectionProgress.test.ts. */
class InspectionV2Test {

    private fun checklist(json: String, strings: Map<String, String> = emptyMap()): InspectionChecklist =
        parseInspectionChecklist(json, strings)!!

    private val groups = checklist(
        """
        {"groups":[
          {"id":"g1","sections":[
            {"id":"s1","title":"Brakes","items":[
              {"id":"pads","text":"Brake pads","type":"select","options":["insp.opt.normal","insp.opt.monitor","insp.opt.needs_repair"]},
              {"id":"fluid","text":"Fluid","options":["Normal","Monitor","Needs Repair","N/A"]},
              {"id":"depth","text":"Depth","type":"measurement","unit":"mm"}
            ]},
            {"id":"s2","title":"Tires","items":[
              {"id":"tires","text":"Tires","type":"corner-select","options":["Normal","Needs Repair"],
               "corners":[{"id":"t_fl","label":"FL"},{"id":"t_fr","label":"FR"}]}
            ]}
          ]},
          {"id":"g2","sections":[{"id":"s3","items":[{"id":"extra","options":["Normal"]}]}]}
        ]}
        """.trimIndent(),
    )

    private val ranged = checklist(
        """
        {"groups":[{"id":"g","sections":[{"id":"s","items":[
          {"id":"pads","options":["Normal","Monitor","Needs Repair"],"required":true},
          {"id":"tread","type":"measurement","min":3,"max":12,"required":true},
          {"id":"press","type":"number-input","inputs":[{"id":"p_f","label":"Front","min":30},{"id":"p_r","label":"Rear"}]},
          {"id":"wheel","type":"wheel-data","max":10}
        ]}]}]}
        """.trimIndent(),
    )

    private val tires = checklist(
        """
        {"groups":[{"id":"g","sections":[{"id":"s","title":"Tires","items":[
          {"id":"tread","text":"Tread","options":["Normal","Monitor","Needs Repair"]},
          {"id":"lights","text":"Lights","options":["Normal","Monitor","Needs Repair"]}
        ]}]}]}
        """.trimIndent(),
    )

    private fun entry(
        logId: String,
        date: String,
        values: Map<String, String>,
        resolved: List<InspectionV2.ResolvedDefect> = emptyList(),
    ): InspectionV2.HistoryEntry {
        val r = InspectionV2.computeResult(tires, values)
        val data = InspectionV2.buildEnvelope(
            stored = null, values = values, subtype = null, mode = null, configSnapshot = null,
            defects = InspectionV2.collectDefects(tires, values), result = r.result,
            outOfService = r.outOfService, newResolved = resolved,
        )
        return InspectionV2.HistoryEntry(logId, "asset", date, "Inspection", data)
    }

    // ---- progress ----

    @Test
    fun `counts select items and corner-select corners, skipping measurements`() {
        val p = InspectionV2.computeProgress(
            groups,
            mapOf("pads" to "insp.opt.needs_repair", "fluid" to "N/A", "t_fl" to "Monitor", "depth" to "3"),
        )
        assertEquals(5, p.total)
        assertEquals(3, p.answered)
        assertEquals(1, p.nr)
        assertEquals(1, p.m)
        assertEquals(0, InspectionV2.computeProgress(groups, mapOf("pads" to "")).answered)
    }

    // ---- defects ----

    @Test
    fun `returns Needs Repair before Monitor with notes and corner labels`() {
        val d = InspectionV2.collectDefects(
            groups,
            mapOf(
                "fluid" to "Monitor",
                "pads" to "Needs Repair",
                InspectionV2.noteKey("pads") to "Front pads at 2mm",
                "t_fr" to "Needs Repair",
            ),
        )
        assertEquals(
            listOf(
                listOf("pads", "NR", null, "Front pads at 2mm"),
                listOf("tires", "NR", "FR", null),
                listOf("fluid", "M", null, null),
            ),
            d.map { listOf(it.itemId, it.bucket.name, it.cornerLabel, it.note) },
        )
        assertEquals("t_fr", d[1].key)
        assertEquals("Brakes", d[0].sectionTitle)
    }

    @Test
    fun `reads legacy android corner keys and localized answers`() {
        val localized = checklist(
            """{"groups":[{"id":"g","sections":[{"id":"s","items":[
              {"id":"tires","type":"corner-select","options":["insp.opt.normal","insp.opt.needs_repair"],"corners":[{"id":"fl","label":"FL"}]}
            ]}]}]}""",
            mapOf("insp.opt.needs_repair" to "Reparatur"),
        )
        val d = InspectionV2.collectDefects(localized, mapOf("tires.fl" to "Reparatur"))
        assertEquals(1, d.size)
        assertEquals("fl", d[0].key)
        assertEquals("insp.opt.needs_repair", d[0].answer)
    }

    @Test
    fun `sorts out-of-service defects first and defaults severity by bucket`() {
        val d = InspectionV2.collectDefects(
            groups,
            mapOf("fluid" to "Monitor", "pads" to "Needs Repair", InspectionV2.sevKey("fluid") to "out_of_service"),
        )
        assertEquals(listOf("fluid" to "out_of_service", "pads" to "major"), d.map { it.itemId to it.severity })
        assertEquals("minor", InspectionV2.collectDefects(groups, mapOf("fluid" to "Monitor"))[0].severity)
    }

    // ---- ranges / result ----

    @Test
    fun `flags low and high values including number inputs and wheel corners`() {
        assertNull(InspectionV2.rangeStatus("", 1.0, 2.0))
        assertNull(InspectionV2.rangeStatus("abc", 1.0, 2.0))
        val issues = InspectionV2.outOfRange(
            ranged,
            mapOf("tread" to "2", "p_f" to "40", "p_r" to "1", "wheel" to """{"fl":"11","fr":"5"}"""),
        )
        assertEquals(
            listOf(Triple("tread", null, "low"), Triple("wheel", "fl", "high")),
            issues.map { Triple(it.key, it.corner, it.status) },
        )
    }

    @Test
    fun `computes pass, pass with defects and fail`() {
        assertEquals("pass", InspectionV2.computeResult(ranged, mapOf("pads" to "Normal", "tread" to "5")).result)
        assertEquals("pass_with_defects", InspectionV2.computeResult(ranged, mapOf("pads" to "Normal", "tread" to "1")).result)
        assertEquals("pass_with_defects", InspectionV2.computeResult(ranged, mapOf("pads" to "Monitor")).result)
        val oos = InspectionV2.computeResult(ranged, mapOf("pads" to "Monitor", InspectionV2.sevKey("pads") to "out_of_service"))
        assertEquals("fail", oos.result)
        assertTrue(oos.outOfService)
        assertEquals("fail", InspectionV2.computeResult(ranged, mapOf("pads" to "Needs Repair")).result)
    }

    // ---- envelope ----

    @Test
    fun `round-trips unknown top-level keys, unknown values and defects`() {
        val stored = InspectionV2.parseDataObject(
            """{"values":{"tread":"Monitor","tread__loc":"fl","webOnly__x":"keep","n":5},
               "mobileOnly":{"a":1},"signOff":{"name":"Ana","signature":null,"signedAt":"2026-01-01"},
               "durationSec":95,"outOfService":true,"defects":[]}""",
        )!!
        val parsed = InspectionV2.parseEnvelope(stored)
        assertEquals("Ana", parsed.signOff?.name)
        val out = InspectionV2.buildEnvelope(
            stored = stored, values = parsed.values, subtype = "DVIR", mode = "Full", configSnapshot = null,
            defects = InspectionV2.collectDefects(tires, parsed.values), result = "pass_with_defects", outOfService = false,
        )
        assertEquals(stored["mobileOnly"], out["mobileOnly"])
        assertEquals(stored["signOff"], out["signOff"])
        assertEquals(stored["durationSec"], out["durationSec"])
        assertEquals(JsonPrimitive(2), out["envelopeVersion"])
        assertFalse(out.containsKey("outOfService"))
        val values = out["values"]!!.jsonObject
        assertEquals("keep", values["webOnly__x"]!!.jsonPrimitive.content)
        assertEquals(stored["values"]!!.jsonObject["n"], values["n"])
        val defects = out["defects"] as JsonArray
        assertEquals("fl", defects[0].jsonObject["location"]!!.jsonPrimitive.content)
        assertEquals("minor", defects[0].jsonObject["severity"]!!.jsonPrimitive.content)
    }

    @Test
    fun `migrates legacy flat android data into values`() {
        val stored = InspectionV2.parseDataObject(
            """{"log_type":"inspection","inspection_subtype":"Base","tread":"Normal","configSnapshot":{"fields":[]},"custom":{"x":1}}""",
        )
        val parsed = InspectionV2.parseEnvelope(stored)
        assertEquals(mapOf("tread" to "Normal"), parsed.values)
        assertEquals("Base", parsed.subtype)
        val out = InspectionV2.buildEnvelope(stored, parsed.values, "Base", null, null, emptyList(), "pass", false)
        assertFalse(out.containsKey("tread"))
        assertEquals("Normal", out["values"]!!.jsonObject["tread"]!!.jsonPrimitive.content)
        assertTrue(out["custom"] is JsonObject)
        assertTrue(out.containsKey("configSnapshot"))
    }

    // ---- open defects ----

    @Test
    fun `keeps a defect open until a later inspection passes it`() {
        val open = InspectionV2.deriveOpenDefects(
            listOf(
                entry("a", "2026-01-01", mapOf("tread" to "Needs Repair", "lights" to "Normal")),
                entry("b", "2026-01-02", mapOf("lights" to "Monitor")),
            ),
        )
        assertEquals(
            listOf(listOf("tread", "a", "open"), listOf("lights", "b", "monitoring")),
            open.map { listOf(it.key, it.sourceLogId, it.status) },
        )
        val after = InspectionV2.deriveOpenDefects(
            listOf(
                entry("a", "2026-01-01", mapOf("tread" to "Needs Repair")),
                entry("c", "2026-01-03", mapOf("tread" to "Normal")),
            ),
        )
        assertTrue(after.isEmpty())
    }

    @Test
    fun `closes defects marked repaired and keeps the first report date`() {
        val open = InspectionV2.deriveOpenDefects(
            listOf(
                entry("a", "2026-01-01", mapOf("tread" to "Needs Repair", "lights" to "Needs Repair")),
                entry(
                    "b", "2026-01-05", mapOf("lights" to "Needs Repair"),
                    listOf(InspectionV2.ResolvedDefect("a", "tread", "tread", "repaired", null, "2026-01-05")),
                ),
            ),
        )
        assertEquals(1, open.size)
        assertEquals("lights", open[0].key)
        assertEquals("a", open[0].sourceLogId)
        assertEquals("2026-01-01", open[0].reportedAt)
        assertEquals("b", open[0].lastSeenLogId)
    }

    @Test
    fun `derives v1 entries from the config snapshot and excludes the current log`() {
        val data = InspectionV2.parseDataObject(
            """{"values":{"tread":"insp.opt.needs_repair"},"configSnapshot":{"inspectionChecklist":{"groups":[
              {"id":"g","sections":[{"id":"s","title":"Tires","items":[{"id":"tread","text":"Tread","options":["Normal","Needs Repair"]}]}]}]}}}""",
        )
        val history = listOf(InspectionV2.HistoryEntry("x", "asset", "2026-01-01", "Inspection", data))
        val open = InspectionV2.deriveOpenDefects(history)
        assertEquals("tread", open[0].key)
        assertEquals("Tread", open[0].label)
        assertEquals("Tires", open[0].sectionTitle)
        assertEquals(Bucket.NR, open[0].bucket)
        assertTrue(InspectionV2.deriveOpenDefects(history, excludeLogId = "x").isEmpty())
    }

    @Test
    fun `maps server register rows`() {
        val row = InspectionV2.parseDataObject(
            """{"defect_id":"d1","item_id":"tires","item_key":"t_fl","label":"Tires","bucket":"M","severity":"weird",
               "source_log_id":"a","reported_at":"2026-01-01","status":"deferred"}""",
        )!!
        val d = InspectionV2.fromServerDefect(row)
        assertEquals("d1", d.id)
        assertEquals("t_fl", d.key)
        assertEquals(Bucket.M, d.bucket)
        assertEquals("major", d.severity)
        assertEquals("2026-01-01", d.lastSeenAt)
        assertEquals("deferred", d.status)
    }
}
