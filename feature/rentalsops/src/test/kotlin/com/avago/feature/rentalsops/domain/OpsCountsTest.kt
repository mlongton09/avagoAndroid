package com.avago.feature.rentalsops.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.core.data.rental.LocalFleet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/** Home tiles derive from P1's LocalFleet (shared with RentalFleetWidget). */
class OpsCountsTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun fleet(id: String, tz: String = "UTC") = RentalRecordEntity(
        entity = LocalFleet.ENTITY_FLEET, id = id, assetId = id,
        json = """{"asset_id":"$id","name":"Unit $id","rental_status":"available","ready":true,"maintenance_open":false,"time_zone":"$tz"}""",
    )

    private fun alloc(id: String, asset: String, kind: String, start: String, end: String?) = RentalRecordEntity(
        entity = LocalFleet.ENTITY_ALLOCATION, id = id, assetId = asset,
        json = """{"id":"$id","asset_id":"$asset","kind":"$kind","active":true,"blocking":true,""" +
            """"start_at":"$start","end_at":${end?.let { "\"$it\"" } ?: "null"}}""",
    )

    @Test
    fun `out includes overdue, due today and overdue are disjoint`() {
        val recs = listOf(
            fleet("A"), fleet("B"), fleet("C"), fleet("D"), fleet("E"),
            alloc("1", "A", "on_rent", "2026-04-28T08:00:00Z", "2026-05-01T09:00:00Z"), // overdue (stale cache)
            alloc("2", "B", "on_rent", "2026-04-28T08:00:00Z", "2026-05-01T17:00:00Z"), // due today
            alloc("3", "C", "on_rent", "2026-04-28T08:00:00Z", "2026-05-04T17:00:00Z"), // out, later
            alloc("4", "D", "reserved", "2026-05-01T12:00:00Z", "2026-05-02T12:00:00Z"), // not out
            // E available
        )
        assertEquals(OpsCounts(out = 3, dueToday = 1, overdue = 1), OpsCounts.of(LocalFleet.compute(recs, now).values))
    }

    @Test
    fun `due today follows the unit's location day`() {
        // 2026-05-02T02:00Z is still 1 May in New York, but 2 May in UTC.
        val ny = listOf(fleet("A", "America/New_York"), alloc("1", "A", "on_rent", "2026-04-28T08:00:00Z", "2026-05-02T02:00:00Z"))
        val utc = listOf(fleet("A", "UTC"), alloc("1", "A", "on_rent", "2026-04-28T08:00:00Z", "2026-05-02T02:00:00Z"))
        assertEquals(1, OpsCounts.of(LocalFleet.compute(ny, now).values).dueToday)
        assertEquals(0, OpsCounts.of(LocalFleet.compute(utc, now).values).dueToday)
    }

    @Test
    fun `empty store`() {
        assertEquals(OpsCounts.EMPTY, OpsCounts.of(emptyList()))
    }
}
