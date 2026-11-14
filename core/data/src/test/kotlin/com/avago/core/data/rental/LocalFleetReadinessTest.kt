package com.avago.core.data.rental

import com.avago.core.data.db.rental.RentalRecordEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Readiness checklist → fleet status (stage B §3): Not ready until passed / waived. */
class LocalFleetReadinessTest {

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun fleet(id: String, extra: String = ""","ready":true""") = RentalRecordEntity(
        entity = LocalFleet.ENTITY_FLEET, id = id, assetId = id,
        json = """{"asset_id":"$id","name":"Unit $id","rental_status":"available","maintenance_open":false$extra}""",
    )

    private fun check(id: String, asset: String, status: String, created: String, state: String = RentalRecordEntity.STATE_SYNCED, version: Long = 1) =
        RentalRecordEntity(
            entity = LocalFleet.ENTITY_READINESS, id = id, assetId = asset, localState = state,
            json = """{"id":"$id","asset_id":"$asset","status":"$status","created_at":"$created","server_version":$version}""",
        )

    /** An (empty) allocation feed so the status is computed on the device. */
    private val allocFeedMeta = RentalRecordEntity(
        entity = LocalFleet.ENTITY_META, id = "allocations_synced", json = """{}""",
    )

    @Test
    fun `newest synced check decides and a device change wins until confirmed`() {
        val latest = LocalFleet.latestReadiness(
            listOf(
                check("c1", "A", "passed", "2026-04-30T08:00:00Z"),
                check("c2", "A", "pending", "2026-05-01T08:00:00Z"),
                check("c3", "B", "pending", "2026-05-01T08:00:00Z"),
                // Older row but edited on the device: the newest intent.
                check("c4", "B", "passed", "2026-04-01T08:00:00Z", RentalRecordEntity.STATE_PENDING),
            ),
        )
        assertEquals("c2", latest.getValue("A").checkId)
        assertTrue(latest.getValue("A").open)
        assertFalse(latest.getValue("A").pending)
        assertEquals("c4", latest.getValue("B").checkId)
        assertFalse(latest.getValue("B").open)
        assertTrue(latest.getValue("B").pending)
    }

    @Test
    fun `open check makes an available unit not ready and passing it frees the unit`() {
        val unit = LocalFleet.unitOf(fleet("A"))!!
        val open = LocalFleet.withReadiness(unit, LocalFleet.ReadinessFlag("c", "failed", false, now))
        assertFalse(open.ready)
        assertEquals(listOf(LocalFleet.READINESS_REASON), open.readiness)
        val passed = LocalFleet.withReadiness(open, LocalFleet.ReadinessFlag("c", "waived", true, now))
        assertTrue(passed.ready)
        assertTrue(passed.readiness.isEmpty())
        // Another reason keeps the unit not ready.
        val other = open.copy(readiness = listOf("inspection_due", LocalFleet.READINESS_REASON))
        assertFalse(LocalFleet.withReadiness(other, LocalFleet.ReadinessFlag("c", "passed", false, now)).ready)
        // No check → card untouched.
        assertEquals(unit, LocalFleet.withReadiness(unit, null))
    }

    @Test
    fun `compute applies the checklist with a pending marker`() {
        val records = listOf(
            fleet("A"),
            fleet("B", ""","ready":false,"readiness":["readiness_checklist"]"""),
            check("p", "A", "pending", "2026-05-01T09:00:00Z", RentalRecordEntity.STATE_PENDING),
            check("q", "B", "passed", "2026-05-01T09:30:00Z", RentalRecordEntity.STATE_PENDING),
            allocFeedMeta,
        )
        val r = LocalFleet.compute(records, now)
        assertEquals(FleetStatus.NOT_READY, r.getValue("A").status)
        assertTrue(r.getValue("A").readinessPending)
        assertEquals(FleetStatus.AVAILABLE, r.getValue("B").status)
        assertTrue(r.getValue("B").readinessPending)
    }

    @Test
    fun `server status kept before the allocation pull still reflects the checklist`() {
        val r = LocalFleet.compute(
            listOf(fleet("A"), check("p", "A", "pending", "2026-05-01T09:00:00Z")),
            now,
        )
        assertEquals(FleetStatus.NOT_READY, r.getValue("A").status)
        assertFalse(r.getValue("A").readinessPending)
        val none = LocalFleet.compute(listOf(fleet("A")), now)
        assertEquals(FleetStatus.AVAILABLE, none.getValue("A").status)
        assertNull(none.getValue("A").checklist)
    }
}
