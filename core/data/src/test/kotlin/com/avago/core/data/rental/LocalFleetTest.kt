package com.avago.core.data.rental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LocalFleetTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun unit(id: String, vararg reasons: String, maintenance: Boolean = false) = LocalFleet.FleetUnit(
        assetId = id, name = "Unit $id", assetType = null, classId = null, className = null, locationId = null,
        timeZone = "UTC", maintenanceOpen = maintenance, readiness = reasons.toList(),
    )

    private fun alloc(id: String, asset: String, kind: String, start: String, end: String?, line: String? = null, expires: String? = null) =
        LocalFleet.Alloc(
            id = id, assetId = asset, kind = kind, blocking = true, startAt = Instant.parse(start), endAt = end?.let(Instant::parse),
            orderId = null, orderLineId = line, expiresAt = expires?.let(Instant::parse),
        )

    @Test
    fun `on rent uses the order line due time not the extended allocation end`() {
        val r = LocalFleet.compute(
            LocalFleet.Inputs(
                units = listOf(unit("A")),
                allocations = listOf(alloc("X", "A", "on_rent", "2026-05-01T06:00:00Z", "2026-05-01T12:00:00Z", line = "L1")),
                lineEnds = mapOf("L1" to Instant.parse("2026-05-01T09:00:00Z")),
            ),
            now,
        )
        assertEquals(FleetStatus.OVERDUE, r.getValue("A").status)
        assertEquals(Instant.parse("2026-05-01T09:00:00Z"), r.getValue("A").dueAt)
    }

    @Test
    fun `reserved within the window, open ended maintenance and not ready`() {
        val r = LocalFleet.compute(
            LocalFleet.Inputs(
                units = listOf(unit("A"), unit("B"), unit("C", LocalFleet.REASON_OPEN_DEFECT)),
                allocations = listOf(
                    alloc("1", "A", "reserved", "2026-05-01T20:00:00Z", "2026-05-03T08:00:00Z"),
                    alloc("2", "B", "maintenance", "2026-05-01T08:00:00Z", null),
                ),
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
    }

    @Test
    fun `an expired hold no longer counts`() {
        val hold = alloc("H", "A", "hold", "2026-05-01T09:00:00Z", "2026-05-02T09:00:00Z", expires = "2026-05-01T09:30:00Z")
        val r = LocalFleet.compute(LocalFleet.Inputs(listOf(unit("A")), listOf(hold)), now)
        assertEquals(FleetStatus.AVAILABLE, r.getValue("A").status)
        assertNull(r.getValue("A").current)
    }

    @Test
    fun `the checklist travels with the result with its pending marker`() {
        val flag = LocalFleet.ReadinessFlag("c", "pending", pending = true, at = now)
        val r = LocalFleet.compute(
            LocalFleet.Inputs(listOf(unit("A", LocalFleet.READINESS_REASON)), emptyList(), checklists = mapOf("A" to flag)),
            now,
        )
        assertEquals(FleetStatus.NOT_READY, r.getValue("A").status)
        assertTrue(r.getValue("A").readinessPending)
        assertTrue(flag.open)
        assertFalse(flag.copy(status = "waived").open)
    }
}
