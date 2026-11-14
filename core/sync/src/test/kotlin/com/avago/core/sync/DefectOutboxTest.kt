package com.avago.core.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefectOutboxTest {

    private fun op(
        status: String = "deferred",
        base: String = "open",
        note: String? = null,
        derived: Boolean = false,
        logId: String? = null,
    ) = DefectOutbox.Op(
        assetId = "asset-1",
        itemKey = "brakes",
        status = status,
        resolutionNote = note,
        baseStatus = base,
        derived = derived,
        logId = logId,
        label = "Brakes",
    )

    private fun row(id: String, status: String, key: String = "brakes", lastSeenLog: String? = null, lastSeenAt: String = "2026-01-01") =
        JsonObject(
            buildMap {
                put("defect_id", JsonPrimitive(id))
                put("asset_id", JsonPrimitive("asset-1"))
                put("item_key", JsonPrimitive(key))
                put("status", JsonPrimitive(status))
                put("bucket", JsonPrimitive("NR"))
                put("last_seen_at", JsonPrimitive(lastSeenAt))
                lastSeenLog?.let { put("last_seen_log_id", JsonPrimitive(it)) }
            },
        )

    @Test
    fun `op payload round-trips through json`() {
        val o = op(status = "repaired", note = "new pads", derived = true, logId = "log-9")
        assertEquals(o, DefectOutbox.Op.fromJson(o.toJson()))
        assertNull(DefectOutbox.Op.fromJson("not json"))
        assertNull(DefectOutbox.Op.fromJson("""{"asset_id":"a"}"""))
    }

    @Test
    fun `merge keeps the first baseline and cancels a change back to it`() {
        val first = (DefectOutbox.merge(null, op(status = "deferred", base = "open")) as DefectOutbox.Merge.Enqueue).op
        val second = DefectOutbox.merge(first, op(status = "repaired", base = "deferred"))
        assertEquals("open", (second as DefectOutbox.Merge.Enqueue).op.baseStatus)
        assertEquals("repaired", second.op.status)
        // Reopening a defect that was open before the queued defer: nothing left to push.
        assertEquals(DefectOutbox.Merge.Cancel, DefectOutbox.merge(first, op(status = "open", base = "deferred")))
        // Same status but with a note still has to reach the server; blank notes are dropped.
        assertTrue(DefectOutbox.merge(null, op(status = "open", base = "open", note = "checked")) is DefectOutbox.Merge.Enqueue)
        assertEquals(DefectOutbox.Merge.Cancel, DefectOutbox.merge(null, op(status = "open", base = "open", note = "  ")))
    }

    @Test
    fun `open and monitoring are the same lifecycle state`() {
        assertTrue(DefectOutbox.sameStatus("open", "monitoring"))
        assertFalse(DefectOutbox.sameStatus("open", "deferred"))
        assertEquals("monitoring", DefectOutbox.localStatus("open", "M"))
        assertEquals("open", DefectOutbox.localStatus("open", "NR"))
        assertEquals("deferred", DefectOutbox.localStatus("deferred", "M"))
    }

    @Test
    fun `decide patches when the server still holds the baseline`() {
        val d = DefectOutbox.decide("d1", op(), listOf(row("d1", "open")))
        assertEquals("d1", (d as DefectOutbox.Decision.Patch).defectId)
        // A monitoring row is still the "open" baseline.
        assertTrue(DefectOutbox.decide("d1", op(), listOf(row("d1", "monitoring"))) is DefectOutbox.Decision.Patch)
    }

    @Test
    fun `decide lets the server win when someone else changed the status`() {
        val d = DefectOutbox.decide("d1", op(status = "deferred", base = "open"), listOf(row("d1", "repaired")))
        assertTrue(d is DefectOutbox.Decision.ServerWins)
    }

    @Test
    fun `decide skips changes the server already has and drops deleted rows`() {
        assertTrue(DefectOutbox.decide("d1", op(status = "deferred"), listOf(row("d1", "deferred"))) is DefectOutbox.Decision.AlreadyApplied)
        assertTrue(DefectOutbox.decide("d1", op(status = "deferred", note = "n"), listOf(row("d1", "deferred"))) is DefectOutbox.Decision.Patch)
        assertEquals(DefectOutbox.Decision.Gone, DefectOutbox.decide("d1", op(), listOf(row("d2", "open"))))
    }

    @Test
    fun `derived changes resolve by inspection then active slot, else wait`() {
        val rows = listOf(
            row("old", "repaired", lastSeenLog = "log-1", lastSeenAt = "2025-01-01"),
            row("new", "open", lastSeenLog = "log-2", lastSeenAt = "2026-02-01"),
            row("other", "open", key = "lights"),
        )
        val byLog = DefectOutbox.decide("asset-1:brakes", op(derived = true, logId = "log-2"), rows)
        assertEquals("new", (byLog as DefectOutbox.Decision.Patch).defectId)
        val bySlot = DefectOutbox.decide("asset-1:brakes", op(derived = true, logId = "log-unsynced"), rows)
        assertEquals("new", (bySlot as DefectOutbox.Decision.Patch).defectId)
        assertEquals(
            DefectOutbox.Decision.NotYetRegistered,
            DefectOutbox.decide("asset-1:brakes", op(derived = true), listOf(row("old", "repaired"))),
        )
    }

    @Test
    fun `only non-retryable 4xx are permanent`() {
        assertTrue(DefectOutbox.isPermanent(400))
        assertTrue(DefectOutbox.isPermanent(403))
        assertFalse(DefectOutbox.isPermanent(408))
        assertFalse(DefectOutbox.isPermanent(429))
        assertFalse(DefectOutbox.isPermanent(503))
        assertFalse(DefectOutbox.isPermanent(-1))
    }

    @Test
    fun `server rows map to cache entities`() {
        val e = DefectOutbox.toEntity(row("d1", "deferred"), 42L)!!
        assertEquals("d1", e.defectId)
        assertEquals("asset-1", e.assetId)
        assertEquals("brakes", e.itemKey)
        assertEquals("deferred", e.status)
        assertEquals("NR", DefectOutbox.bucketOf(e.rowJson))
        assertNull(DefectOutbox.toEntity(JsonObject(mapOf("status" to JsonPrimitive("open"))), 0L))
    }
}
