package com.avago.feature.log.model

import com.avago.feature.log.model.InspectionV2.toJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Round-trip safety of a web-written v2 envelope through the Android editor (fixtures/web_inspection_v2.json). */
class InspectionEnvelopeRoundTripTest {

    private val stored: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("fixtures/web_inspection_v2.json")!!.readText(),
    ).jsonObject

    private val storedValues: JsonObject get() = stored["values"]!!.jsonObject

    /** What the VM does on save: answers seeded from the stored values, minus user-cleared keys. */
    private fun save(
        answers: Map<String, String>,
        cleared: Set<String> = emptySet(),
        newResolved: List<InspectionV2.ResolvedDefect> = emptyList(),
        signOff: kotlinx.serialization.json.JsonElement? = null,
        certification: kotlinx.serialization.json.JsonElement? = null,
    ): JsonObject {
        val parsed = InspectionV2.parseEnvelope(stored)
        val values = InspectionV2.mergeValues(parsed.values, answers, cleared)
        val summary = InspectionV2.computeResult(null, values)
        return InspectionV2.buildEnvelope(
            stored = stored,
            values = answers,
            subtype = parsed.subtype,
            mode = parsed.mode,
            configSnapshot = null,
            defects = parsed.defects,
            result = summary.result,
            outOfService = parsed.outOfService,
            newResolved = newResolved,
            clearedKeys = cleared,
            signOff = signOff,
            certification = certification,
        )
    }

    @Test
    fun `unchanged edit keeps every value with its original JSON type`() {
        val parsed = InspectionV2.parseEnvelope(stored)
        val out = save(parsed.values)
        assertEquals(storedValues, out["values"])
        assertEquals(JsonPrimitive(2.5), out["values"]!!.jsonObject["depth"])
        assertEquals(JsonNull, out["values"]!!.jsonObject["nullable_key"])
        assertTrue(out["values"]!!.jsonObject["photo_meta"] is JsonObject)
    }

    @Test
    fun `partial edit map never drops keys it does not mention`() {
        val out = save(mapOf("fluid" to "Normal"))
        val values = out["values"]!!.jsonObject
        assertEquals(storedValues.keys, values.keys)
        assertEquals("Normal", values["fluid"]!!.jsonPrimitive.content)
        assertEquals("Added on web", values["web_only_item__note"]!!.jsonPrimitive.content)
    }

    @Test
    fun `web set sev loc and note survive edits to other items`() {
        val parsed = InspectionV2.parseEnvelope(stored)
        val out = save(parsed.values + ("t_fl" to "Needs Repair"))
        val values = out["values"]!!.jsonObject
        assertEquals("out_of_service", values["pads__sev"]!!.jsonPrimitive.content)
        assertEquals("fl", values["pads__loc"]!!.jsonPrimitive.content)
        assertEquals("Pad worn to backing plate", values["pads__note"]!!.jsonPrimitive.content)
        assertEquals("engine", values["fluid__loc"]!!.jsonPrimitive.content)
        assertEquals("major", values["depth__sev"]!!.jsonPrimitive.content)
    }

    @Test
    fun `explicitly cleared key is removed and only that key`() {
        val parsed = InspectionV2.parseEnvelope(stored)
        val out = save(parsed.values - "fluid__loc", cleared = setOf("fluid__loc"))
        val values = out["values"]!!.jsonObject
        assertFalse("fluid__loc" in values)
        assertEquals(storedValues.keys - "fluid__loc", values.keys)
    }

    @Test
    fun `cleared then re-entered key keeps the new value`() {
        val out = save(mapOf("pads__note" to "Replaced"), cleared = setOf("pads__note"))
        assertEquals("Replaced", out["values"]!!.jsonObject["pads__note"]!!.jsonPrimitive.content)
    }

    @Test
    fun `resolvedDefects certification signOff and unknown top level keys survive`() {
        val out = save(InspectionV2.parseEnvelope(stored).values)
        assertEquals(stored["resolvedDefects"], out["resolvedDefects"])
        assertEquals(stored["certification"], out["certification"])
        assertEquals(stored["signOff"], out["signOff"])
        assertEquals(stored["webOnlyTopLevel"], out["webOnlyTopLevel"])
        assertEquals(stored["durationSec"], out["durationSec"])
        assertEquals(stored["configSnapshot"], out["configSnapshot"])
    }

    @Test
    fun `new resolution is appended to stored resolvedDefects`() {
        val r = InspectionV2.ResolvedDefect(null, "pads", "pads", "still_present", null, "2026-02-01T00:00:00Z")
        val out = save(emptyMap(), newResolved = listOf(r))
        val arr = out["resolvedDefects"] as JsonArray
        assertEquals(2, arr.size)
        assertEquals(stored["resolvedDefects"]!!.let { (it as JsonArray)[0] }, arr[0])
    }

    @Test
    fun `signOff and certification can be replaced or explicitly nulled`() {
        val so = InspectionV2.SignOff("A", null, "A", "2026-02-01T00:00:00Z").toJson()
        val out = save(emptyMap(), signOff = so, certification = JsonNull)
        assertEquals(so, out["signOff"])
        assertEquals(JsonNull, out["certification"])
    }

    @Test
    fun `certification keeps unknown keys and writes the android mechanic signature`() {
        val extra = buildMap {
            putAll(stored["certification"]!!.jsonObject)
            put("webFuture", JsonPrimitive(1))
        }.let(::JsonObject)
        val parsed = InspectionV2.parseEnvelope(stored).certification!!
        val json = parsed.copy(signature = "data:image/png;base64,AA==").toJson(extra)
        assertEquals("corrected", json["status"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(1), json["webFuture"])
        assertEquals("data:image/png;base64,AA==", json["signature"]!!.jsonPrimitive.content)
        assertNull(InspectionV2.parseEnvelope(stored).certification!!.signature)
    }

    @Test
    fun `rebase re-applies my edits on top of their values`() {
        val original = InspectionV2.parseEnvelope(stored).values
        val mineNow = original + ("fluid" to "Normal") + ("pads__note" to "Mine") - "fluid__loc"
        val theirs = original + ("pads__note" to "Theirs") + ("web_only_item" to "Needs Repair")
        val mine = InspectionV2.diffValues(original, mineNow)
        assertEquals(setOf("fluid", "pads__note"), mine.changed.keys)
        assertEquals(setOf("fluid__loc"), mine.cleared)

        val rebased = InspectionV2.rebaseValues(theirs, mine)
        assertEquals("Mine", rebased["pads__note"])
        assertEquals("Normal", rebased["fluid"])
        assertEquals("Needs Repair", rebased["web_only_item"])
        assertFalse("fluid__loc" in rebased)
    }

    @Test
    fun `out of range reading marked out of service fails the inspection`() {
        val checklist = parseInspectionChecklist(
            """{"groups":[{"id":"g","sections":[{"id":"s","items":[{"id":"tread","type":"measurement","min":3}]}]}]}""",
        )
        val r = InspectionV2.computeResult(checklist, mapOf("tread" to "1", "tread__sev" to "out_of_service"))
        assertTrue(r.outOfService)
        assertEquals(InspectionV2.RESULT_FAIL, r.result)
        val minor = InspectionV2.computeResult(checklist, mapOf("tread" to "1", "tread__sev" to "minor"))
        assertFalse(minor.outOfService)
        assertEquals(InspectionV2.RESULT_PASS_WITH_DEFECTS, minor.result)
    }
}
