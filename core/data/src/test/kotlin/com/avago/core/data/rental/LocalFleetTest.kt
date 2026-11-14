package com.avago.core.data.rental

import com.avago.core.data.db.rental.RentalRecordEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LocalFleetTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun fleet(id: String, extra: String = "") = RentalRecordEntity(
        entity = LocalFleet.ENTITY_FLEET, id = id,
        json = """{"asset_id":"$id","name":"Unit $id","rental_status":"available","ready":true,"maintenance_open":false$extra}""",
        assetId = id,
    )

    private fun alloc(id: String, asset: String, kind: String, start: String, end: String?, extra: String = "") =
        RentalRecordEntity(
            entity = LocalFleet.ENTITY_ALLOCATION, id = id, assetId = asset,
            json = """{"id":"$id","asset_id":"$asset","kind":"$kind","active":true,"blocking":true,""" +
                """"start_at":"$start","end_at":${end?.let { "\"$it\"" } ?: "null"}$extra}""",
        )

    @Test
    fun `on rent uses the order line due time not the extended allocation end`() {
        val line = RentalRecordEntity(
            entity = LocalFleet.ENTITY_ORDER_LINE, id = "L1",
            json = """{"id":"L1","end_at":"2026-05-01T09:00:00Z"}""",
        )
        val r = LocalFleet.compute(
            listOf(
                fleet("A"),
                alloc("X", "A", "on_rent", "2026-05-01T06:00:00Z", "2026-05-01T12:00:00Z", ""","order_line_id":"L1""""),
                line,
            ),
            now,
        )
        assertEquals(FleetStatus.OVERDUE, r.getValue("A").status)
    }

    @Test
    fun `reserved within the window and open ended maintenance`() {
        val r = LocalFleet.compute(
            listOf(
                fleet("A"), fleet("B"), fleet("C", ""","ready":false,"readiness":["inspection_due"]"""),
                alloc("1", "A", "reserved", "2026-05-01T20:00:00Z", "2026-05-03T08:00:00Z"),
                alloc("2", "B", "maintenance", "2026-05-01T08:00:00Z", null),
            ),
            now,
        )
        assertEquals(FleetStatus.RESERVED, r.getValue("A").status)
        assertEquals(FleetStatus.IN_MAINTENANCE, r.getValue("B").status)
        assertEquals(FleetStatus.NOT_READY, r.getValue("C").status)
        val (counts, dueToday) = LocalFleet.counts(r.values)
        assertEquals(1, counts[FleetStatus.RESERVED])
        assertEquals(0, counts[FleetStatus.AVAILABLE])
        assertEquals(0, dueToday)
        assertTrue(r.values.all { it.local })
    }

    @Test
    fun `inactive allocations are ignored and server status is kept before the first allocation pull`() {
        val inactive = alloc("1", "A", "on_rent", "2026-05-01T06:00:00Z", null).let {
            it.copy(json = it.json.replace("\"active\":true", "\"active\":false"))
        }
        assertEquals(null, LocalFleet.allocOf(inactive))
        val serverOnly = LocalFleet.compute(
            listOf(fleet("A").let { it.copy(json = it.json.replace("available", "on_rent")) }),
            now,
        )
        assertEquals(FleetStatus.ON_RENT, serverOnly.getValue("A").status)
        assertFalse(serverOnly.getValue("A").local)
    }
}
