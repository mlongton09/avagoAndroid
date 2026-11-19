package com.avago.feature.workorders.model

import com.avago.core.data.domain.TimeEntry
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

class WoV3Test {

    private val fullCloseout = WoCloseout(
        failureCode = "F-WEAR", causeCode = "C-NWEAR", remedyCode = "R-REPL",
        resolutionNotes = "Replaced pads", meterReading = 12345.0,
    )

    @Test
    fun `merge preserves unknown keys and null clears`() {
        val raw = """{"custom":{"a":1},"hold":{"reason":"parts"},"n":5}"""
        val merged = mergeWoAttributes(
            parseWoAttributes(raw),
            mapOf(WO_KEY_HOLD to JsonNull, WO_KEY_VENDOR to WoVendor(vendorName = "Acme").toJson()),
        )
        assertEquals(1, merged["custom"]!!.jsonObject["a"]!!.jsonPrimitive.content.toInt())
        assertEquals("5", merged["n"]!!.jsonPrimitive.content)
        assertNull(merged["hold"])
        assertEquals("Acme", merged["vendor"]!!.jsonObject["vendor_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `closeout roundtrip keeps unknown nested keys`() {
        val attrs = parseWoAttributes("""{"closeout":{"failure_code":"F-LEAK","x_extra":"keep"}}""")
        val c = WoCloseout.from(attrs).copy(causeCode = "C-UNK")
        val json = c.toJson()
        assertEquals("keep", json["x_extra"]!!.jsonPrimitive.content)
        assertEquals("F-LEAK", json["failure_code"]!!.jsonPrimitive.content)
        assertEquals("C-UNK", json["cause_code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `closeout gate requires codes notes and meter when asset`() {
        val empty = validateCloseout(WoCloseout(), hasAsset = true, checklist = emptyList())
        assertFalse(empty.ok)
        assertEquals(
            listOf("failure_code", "cause_code", "remedy_code", "resolution_notes", "meter_reading"),
            empty.missing,
        )
        assertTrue(validateCloseout(fullCloseout, true, emptyList()).ok)
        assertTrue(validateCloseout(fullCloseout.copy(meterReading = null), false, emptyList()).ok)
    }

    @Test
    fun `closeout gate blocks incomplete required checklist rows only`() {
        val rows = listOf(ChecklistRowState(required = false, completed = false), ChecklistRowState(true, true))
        assertTrue(validateCloseout(fullCloseout, true, rows).ok)
        val blocked = validateCloseout(fullCloseout, true, rows + ChecklistRowState(true, false))
        assertEquals(listOf("checklist"), blocked.missing)
        assertFalse(isChecklistRowRequired("heading"))
        assertTrue(isChecklistRowRequired(null))
    }

    @Test
    fun `procedure row required rule by type with explicit override`() {
        listOf("STEP", "CHECKBOX", "NUMBER", "METER", "CYCLE_COUNT", "SELECT", "SIGNATURE", null, "weird")
            .forEach { assertTrue(isChecklistRowRequired(it), "$it should be required") }
        listOf("TEXT", "DATE", "file").forEach { assertFalse(isChecklistRowRequired(it), "$it should be optional") }
        assertFalse(isChecklistRowRequired("HEADING", explicitRequired = true))
        assertTrue(isChecklistRowRequired("TEXT", explicitRequired = true))
        assertFalse(isChecklistRowRequired("STEP", explicitRequired = false))
    }

    @Test
    fun `closeout optional downtime and signature when configured`() {
        val opts = parseCloseoutOptions("""{"wo_closeout":{"require_signature":true,"require_downtime":true}}""")
        val r = validateCloseout(fullCloseout, true, emptyList(), opts)
        assertEquals(listOf("downtime", "signoff"), r.missing)
        val ok = fullCloseout.copy(
            downtimeStart = "2024-01-01T10:00:00Z", downtimeEnd = "2024-01-01T11:30:00Z",
            signoff = WoSignoff("Pat", "2024-01-01T11:31:00Z"),
        )
        assertTrue(validateCloseout(ok, true, emptyList(), opts).ok)
    }

    @Test
    fun `downtime minutes rounds and rejects reversed`() {
        assertEquals(90L, downtimeMinutes("2024-01-01T10:00:00Z", "2024-01-01T11:30:00Z"))
        assertNull(downtimeMinutes("2024-01-01T12:00:00Z", "2024-01-01T11:30:00Z"))
        assertNull(downtimeMinutes(null, "2024-01-01T11:30:00Z"))
    }

    @Test
    fun `codes fall back to defaults when server list empty`() {
        val d = parseWoCodes(JsonObject(mapOf("codes" to kotlinx.serialization.json.JsonArray(emptyList()))), WoCodeKind.CAUSE)
        assertEquals("C-NWEAR", d.first().code)
        assertEquals("default:C-NWEAR", d.first().codeId)
        assertEquals(12, DEFAULT_WO_CODES.getValue(WoCodeKind.FAILURE).size)
        val server = parseWoCodes(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"codes":[{"code_id":"x","kind":"remedy","code":"R-X","label":"Custom","sort_order":0}]}""",
            ),
            WoCodeKind.REMEDY,
        )
        assertEquals(listOf("R-X"), server.map { it.code })
    }

    @Test
    fun `sla respond phase warn and breach`() {
        val h = 3_600_000L
        val created = 0L
        val ok = slaState("open", "high", created, null, JsonObject(emptyMap()), DEFAULT_SLA_POLICY, 1 * h)
        assertEquals(SlaPhase.RESPOND, ok.phase)
        assertEquals(SlaLevel.OK, ok.level)
        val warn = slaState("open", "high", created, null, JsonObject(emptyMap()), DEFAULT_SLA_POLICY, 3 * h)
        assertEquals(SlaLevel.WARN, warn.level)
        val breach = slaState("open", "high", created, null, JsonObject(emptyMap()), DEFAULT_SLA_POLICY, 5 * h)
        assertEquals(SlaLevel.BREACH, breach.level)
    }

    @Test
    fun `sla resolve phase, override and met`() {
        val h = 3_600_000L
        val resolve = slaState("in_progress", "high", 0, null, JsonObject(emptyMap()), DEFAULT_SLA_POLICY, 5 * h)
        assertEquals(SlaPhase.RESOLVE, resolve.phase)
        assertEquals(SlaLevel.OK, resolve.level)
        assertEquals(24 * h, resolve.dueAtMs)
        val over = parseWoAttributes("""{"sla":{"resolve_hours":2}}""")
        assertEquals(SlaLevel.BREACH, slaState("in_progress", "high", 0, null, over, DEFAULT_SLA_POLICY, 5 * h).level)
        val met = slaState("completed", "high", 0, 10 * h, JsonObject(emptyMap()), DEFAULT_SLA_POLICY, 50 * h)
        assertEquals(SlaPhase.MET, met.phase)
        assertEquals(SlaLevel.OK, met.level)
        assertEquals(SlaPhase.NONE, slaState("cancelled", "high", 0, null, JsonObject(emptyMap()), DEFAULT_SLA_POLICY, 0).phase)
    }

    @Test
    fun `sla policy merges server over defaults`() {
        val p = parseSlaPolicy(kotlinx.serialization.json.Json.parseToJsonElement("""{"policy":{"high":{"respond_hours":2}}}"""))
        assertEquals(SlaWindow(2.0, 24.0), p["high"])
        assertEquals(DEFAULT_SLA_POLICY["low"], p["low"])
    }

    @Test
    fun `timer state sums finished entries and runs from the open one`() {
        fun entry(start: Long, end: Long?, breakMin: Int = 0) =
            TimeEntry(id = "t$start", workerId = "w", workOrderId = "wo", start = start, end = end, breakMinutes = breakMin, status = "open")
        val idle = WoTimerState.of(listOf(entry(0, 120_000, breakMin = 1), entry(200_000, 230_000)))
        assertFalse(idle.running)
        assertEquals(90L, idle.totalSeconds)
        assertEquals(90L, idle.elapsedSeconds(999_999))

        val running = WoTimerState.of(listOf(entry(0, 60_000), entry(100_000, null)))
        assertTrue(running.running)
        assertEquals(100_000L, running.startedAtMs)
        assertEquals(60L + 50L, running.elapsedSeconds(150_000))
        assertEquals(WoTimerState.IDLE, WoTimerState.of(emptyList()))
    }
}
