package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import com.avago.feature.rentals.data.RentalRequests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Line swap optimistic apply (stage B §2 "Swap"). */
class LineSwapTest {
    private val at = Instant.parse("2026-05-02T12:00:00Z")
    private val now = at

    private val line = buildJsonObject {
        put("id", "L1"); put("order_id", "O1"); put("kind", "rental"); put("asset_id", "A")
        put("class_id", "K"); put("status", "out"); put("description", "Van A")
        put("start_at", "2026-05-01T08:00:00Z"); put("end_at", "2026-05-05T08:00:00Z")
        put("amount_minor", 40000); put("client_uuid", "u1"); put("server_version", 7)
    }

    private fun alloc(id: String, asset: String, lineId: String?, active: Boolean = true) = id to buildJsonObject {
        put("id", id); put("asset_id", asset); put("order_id", "O1"); lineId?.let { put("order_line_id", it) }
        put("kind", "on_rent"); put("active", active)
        put("start_at", "2026-05-01T08:00:00Z"); put("end_at", "2026-05-05T08:00:00Z")
    }

    @Test
    fun `only checked out rental lines are swappable`() {
        assertTrue(LineSwap.swappable(line, OrderStatus.ON_RENT))
        assertFalse(LineSwap.swappable(JsonObject(line + ("status" to kotlinx.serialization.json.JsonPrimitive("returned"))), OrderStatus.ON_RENT))
        assertFalse(LineSwap.swappable(JsonObject(line + ("kind" to kotlinx.serialization.json.JsonPrimitive("fee"))), OrderStatus.ON_RENT))
        assertFalse(LineSwap.swappable(JsonObject(line - "asset_id"), OrderStatus.ON_RENT))
        // Checked out on the device, not synced yet: the order is on rent locally.
        val open = JsonObject(line + ("status" to kotlinx.serialization.json.JsonPrimitive("open")))
        assertTrue(LineSwap.swappable(open, OrderStatus.ON_RENT))
        assertFalse(LineSwap.swappable(open, "reserved"))
    }

    @Test
    fun `plan swaps the old line releases its allocation and books the replacement tentatively`() {
        val allocs = listOf(alloc("X1", "A", "L1"), alloc("X2", "B2", "L2"), alloc("X3", "A", "L1", active = false))
        val p = LineSwap.plan("O1", "L1", line, allocs, "B", "L9", "X9", at)

        assertEquals("swapped", p.oldLine.str("status"))
        assertEquals(at.toString(), p.oldLine.str("end_at"))
        assertEquals(at.toString(), p.oldLine.str("actual_in_at"))
        assertEquals(line, p.oldLine[LineSwap.KEY_BASE])

        val n = p.newLine
        assertEquals("L9", n.str("id"))
        assertEquals("B", n.str("asset_id"))
        assertEquals("K", n.str("class_id"))
        assertEquals("out", n.str("status"))
        assertEquals(at.toString(), n.str("start_at"))
        assertEquals("2026-05-05T08:00:00Z", n.str("end_at"))
        assertEquals("L1", n.str("swapped_from_line_id"))
        assertEquals(true, n.bool("estimate"))
        // Server-owned fields are not copied (the server prices the new line).
        listOf("amount_minor", "client_uuid", "server_version", "description", LineSwap.KEY_BASE).forEach { assertNull(n[it], it) }

        assertEquals(listOf("X1"), p.released.map { it.first })
        val rel = p.released.single().second
        assertEquals(false, rel.bool("active"))
        assertEquals(at.toString(), rel.str("end_at"))
        assertTrue(rel[LineSwap.KEY_BASE] is JsonObject)

        val a = p.newAllocation
        assertEquals("X9", a.str("id"))
        assertEquals("B", a.str("asset_id"))
        assertEquals("L9", a.str("order_line_id"))
        assertEquals("on_rent", a.str("kind"))
        assertEquals(true, a.bool("tentative"))
        assertEquals(at.toString(), a.str("start_at"))
        assertEquals("2026-05-05T08:00:00Z", a.str("end_at"))
    }

    @Test
    fun `plan releases an allocation without line id on the old unit and rejects the same unit`() {
        val p = LineSwap.plan("O1", "L1", line, listOf(alloc("X1", "A", null), alloc("X2", "C", null)), "B", "L9", "X9", at)
        assertEquals(listOf("X1"), p.released.map { it.first })
        assertThrows(IllegalArgumentException::class.java) {
            LineSwap.plan("O1", "L1", line, emptyList(), "A", "L9", "X9", at)
        }
    }

    @Test
    fun `planned rows free the old unit and occupy the replacement in local availability`() {
        val allocs = listOf(alloc("X1", "A", "L1"))
        val p = LineSwap.plan("O1", "L1", line, allocs, "B", "L9", "X9", at)
        fun rec(entity: String, o: JsonObject) = RentalRecordEntity(entity = entity, id = o.str("id")!!, json = o.toString(), assetId = o.str("asset_id"))
        val rows = listOf(
            rec(LocalFleet.ENTITY_ALLOCATION, p.released.single().second),
            rec(LocalFleet.ENTITY_ALLOCATION, p.newAllocation),
            rec(LocalFleet.ENTITY_ORDER_LINE, p.newLine),
        )
        val busy = LocalSchedule.busy(rows)
        val later = at.plusSeconds(3600)
        assertTrue(Availability.unit("A", busy, later, later.plusSeconds(3600), now).available)
        assertFalse(Availability.unit("B", busy, later, later.plusSeconds(3600), now).available)
    }

    private fun fleetRow(id: String, cls: String?, ready: Boolean = true) = RentalRecordEntity(
        entity = LocalFleet.ENTITY_FLEET, id = id, assetId = id,
        json = """{"asset_id":"$id","name":"Unit $id","rental_class_id":${cls?.let { "\"$it\"" } ?: "null"},""" +
            """"rental_status":"available","ready":$ready,"maintenance_open":false}""",
    )

    @Test
    fun `candidates are free ready units of the same class except the old one`() {
        val fleetRows = listOf(
            fleetRow("A", "K"), fleetRow("C", "K"), fleetRow("B", "K"),
            fleetRow("D", "K", ready = false),
            fleetRow("E", "Z"),
            fleetRow("F", "K"),
        )
        val busyRow = RentalRecordEntity(
            entity = LocalFleet.ENTITY_ALLOCATION, id = "Y", assetId = "F",
            json = """{"id":"Y","asset_id":"F","kind":"reserved","active":true,"blocking":true,"start_at":"2026-05-03T00:00:00Z","end_at":"2026-05-04T00:00:00Z"}""",
        )
        val records = fleetRows + busyRow
        val fleet = LocalFleet.compute(records, now).values
        val c = LineSwap.candidates(fleet, LocalSchedule.busy(records), "K", "A", at, Instant.parse("2026-05-05T08:00:00Z"), now)
        assertEquals(listOf("B", "C"), c.map { it.assetId })
        // No class on the line: the old unit's class is used.
        val byOld = LineSwap.candidates(fleet, LocalSchedule.busy(records), null, "A", at, Instant.parse("2026-05-05T08:00:00Z"), now)
        assertEquals(listOf("B", "C"), byOld.map { it.assetId })
    }

    @Test
    fun `swap body nests the readings of both sides`() {
        val body = RentalRequests.swap(
            "u-1", at, "B", " flat tyre ",
            RentalRequests.swapSide(1234.50, 80),
            RentalRequests.swapSide(null, null),
        )
        assertEquals("u-1", body.str("client_uuid"))
        assertEquals(at.toString(), body.str("at"))
        assertEquals("B", body.str("new_asset_id"))
        assertEquals("flat tyre", body.str("reason"))
        val checkin = body["checkin"]!!.jsonObject
        assertEquals("1234.5", checkin.str("meter_value"))
        assertEquals(80L, checkin.long("fuel_level_pct"))
        assertNull(body["checkout"])
    }
}
