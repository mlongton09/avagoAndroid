package com.avago.feature.rentals.ops.domain

import com.avago.core.data.rental.LocalFleet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/** Home tiles derive from P1's LocalFleet (shared with RentalFleetWidget). */
class OpsCountsTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun fleet(id: String, tz: String = "UTC") = LocalFleet.FleetUnit(
        assetId = id, name = "Unit $id", assetType = null, classId = null, className = null, locationId = null,
        timeZone = tz, maintenanceOpen = false, readiness = emptyList(),
    )

    private fun alloc(id: String, asset: String, kind: String, start: String, end: String?) = LocalFleet.Alloc(
        id = id, assetId = asset, kind = kind, blocking = true, startAt = Instant.parse(start), endAt = end?.let(Instant::parse),
        orderId = null, orderLineId = null, expiresAt = null,
    )

    private fun compute(units: List<LocalFleet.FleetUnit>, allocs: List<LocalFleet.Alloc>) =
        LocalFleet.compute(LocalFleet.Inputs(units, allocs), now).values

    @Test
    fun `out includes overdue, due today and overdue are disjoint`() {
        val units = listOf(fleet("A"), fleet("B"), fleet("C"), fleet("D"), fleet("E"))
        val allocs = listOf(
            alloc("1", "A", "on_rent", "2026-04-28T08:00:00Z", "2026-05-01T09:00:00Z"), // overdue (stale cache)
            alloc("2", "B", "on_rent", "2026-04-28T08:00:00Z", "2026-05-01T17:00:00Z"), // due today
            alloc("3", "C", "on_rent", "2026-04-28T08:00:00Z", "2026-05-04T17:00:00Z"), // out, later
            alloc("4", "D", "reserved", "2026-05-01T12:00:00Z", "2026-05-02T12:00:00Z"), // not out
            // E available
        )
        assertEquals(OpsCounts(out = 3, dueToday = 1, overdue = 1), OpsCounts.of(compute(units, allocs)))
    }

    @Test
    fun `due today follows the unit's location day`() {
        // 2026-05-02T02:00Z is still 1 May in New York, but 2 May in UTC.
        val a = listOf(alloc("1", "A", "on_rent", "2026-04-28T08:00:00Z", "2026-05-02T02:00:00Z"))
        assertEquals(1, OpsCounts.of(compute(listOf(fleet("A", "America/New_York")), a)).dueToday)
        assertEquals(0, OpsCounts.of(compute(listOf(fleet("A", "UTC")), a)).dueToday)
    }

    @Test
    fun `empty store`() {
        assertEquals(OpsCounts.EMPTY, OpsCounts.of(emptyList()))
    }
}
