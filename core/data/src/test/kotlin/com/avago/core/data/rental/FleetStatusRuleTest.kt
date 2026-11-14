package com.avago.core.data.rental

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Runs the shared fleet-status fixtures (copied from AvagoSvc
 * `tests/fixtures/rental_status_cases.json`) — the server, web, iOS and Android must all
 * produce `expected` for every `input`.
 */
class FleetStatusRuleTest {

    private val doc: JsonObject = Json.parseToJsonElement(
        requireNotNull(javaClass.classLoader?.getResource("rental_status_cases.json")) { "fixture missing" }.readText(),
    ).jsonObject

    private fun JsonElement?.str(): String? = (this as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun input(o: JsonObject): FleetInput {
        val defaults = doc["defaults"]?.jsonObject ?: JsonObject(emptyMap())
        fun field(k: String) = o[k] ?: defaults[k]
        return FleetInput(
            now = Instant.parse(o.getValue("now").str()),
            timeZone = field("time_zone").str(),
            reservedWindowMinutes = field("reserved_window_minutes")?.jsonPrimitive?.longOrNull
                ?: FleetInput.DEFAULT_RESERVED_WINDOW_MINUTES,
            maintenanceOpen = field("maintenance_open")?.jsonPrimitive?.booleanOrNull ?: false,
            ready = field("ready")?.jsonPrimitive?.booleanOrNull ?: true,
            allocations = (field("allocations")?.jsonArray ?: emptyList()).map { a ->
                val ao = a.jsonObject
                FleetAlloc(
                    kind = ao.getValue("kind").str()!!,
                    startAt = Instant.parse(ao.getValue("start_at").str()),
                    endAt = ao["end_at"].str()?.let(Instant::parse),
                    blocking = ao["blocking"]?.jsonPrimitive?.booleanOrNull ?: true,
                )
            },
        )
    }

    @Test
    fun `shared fixtures match the server rule`() {
        val cases = doc.getValue("cases").jsonArray
        assertTrue(cases.size >= 15, "expected at least 15 fixture cases, got ${cases.size}")
        cases.forEach { c ->
            val co = c.jsonObject
            val name = co.getValue("name").str()
            val expected = co.getValue("expected").jsonObject
            val got = FleetStatusRule.compute(input(co.getValue("input").jsonObject))
            assertEquals(expected.getValue("status").str(), got.status.wire, "$name: status")
            assertEquals(expected["due_back_today"]?.jsonPrimitive?.booleanOrNull ?: false, got.dueBackToday, "$name: due_back_today")
            assertEquals(expected["next_change_at"].str()?.let(Instant::parse), got.nextChangeAt, "$name: next_change_at")
        }
    }

    @Test
    fun `wire names round trip`() {
        FleetStatus.entries.forEach { assertEquals(it, FleetStatus.fromWire(it.wire)) }
    }

    @Test
    fun `invalid time zone falls back to UTC`() {
        val out = FleetStatusRule.compute(
            FleetInput(
                now = Instant.parse("2026-05-01T10:00:00Z"),
                timeZone = "Not/AZone",
                allocations = listOf(
                    FleetAlloc("on_rent", Instant.parse("2026-04-30T08:00:00Z"), Instant.parse("2026-05-01T20:00:00Z")),
                ),
            ),
        )
        assertEquals(FleetStatus.ON_RENT, out.status)
        assertTrue(out.dueBackToday)
    }
}
