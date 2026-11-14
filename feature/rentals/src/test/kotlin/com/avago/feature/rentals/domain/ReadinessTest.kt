package com.avago.feature.rentals.domain

import com.avago.core.data.db.rental.RentalRecordEntity
import com.avago.feature.rentals.data.RentalRequests
import com.avago.feature.rentals.ui.readinessUi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Readiness checklist state (stage B §3): transitions, validation, local apply, wire shape. */
class ReadinessTest {
    private val at = Instant.parse("2026-05-01T10:00:00Z")
    private val allDone = Readiness.DEFAULT_ITEMS.map { it.copy(done = true) }

    private fun rec(json: JsonObject, state: String = RentalRecordEntity.STATE_SYNCED) = RentalRecordEntity(
        entity = RentalEntity.READINESS_CHECK, id = json.str("id")!!, json = json.toString(),
        assetId = json.str("asset_id"), localState = state,
    )

    @Test
    fun `only open checks change and the target must be a known status`() {
        assertTrue(Readiness.canTransition(Readiness.PENDING, Readiness.PASSED))
        assertTrue(Readiness.canTransition(Readiness.FAILED, Readiness.WAIVED))
        assertFalse(Readiness.canTransition(Readiness.PASSED, Readiness.FAILED))
        assertFalse(Readiness.canTransition(Readiness.WAIVED, Readiness.PENDING))
        assertFalse(Readiness.canTransition(Readiness.PENDING, "ready"))
    }

    @Test
    fun `validation mirrors the server rules`() {
        val v = { from: String, to: String, items: List<Readiness.Item>, notes: String?, waive: Boolean ->
            Readiness.validate(from, to, items, notes, waive)
        }
        assertEquals(Readiness.Problem.INCOMPLETE, v(Readiness.PENDING, Readiness.PASSED, Readiness.DEFAULT_ITEMS, null, false))
        assertNull(v(Readiness.PENDING, Readiness.PASSED, allDone, null, false))
        // Waive: permission first, then a reason (server 403 / 400 reason_required).
        assertEquals(Readiness.Problem.NOT_ALLOWED, v(Readiness.PENDING, Readiness.WAIVED, allDone, "ok", false))
        assertEquals(Readiness.Problem.REASON_REQUIRED, v(Readiness.PENDING, Readiness.WAIVED, allDone, "  ", true))
        assertNull(v(Readiness.FAILED, Readiness.WAIVED, Readiness.DEFAULT_ITEMS, "customer collecting", true))
        // Fail: unticked items explain it; with everything ticked a reason is needed.
        assertNull(v(Readiness.PENDING, Readiness.FAILED, Readiness.DEFAULT_ITEMS, null, false))
        assertEquals(Readiness.Problem.REASON_REQUIRED, v(Readiness.PENDING, Readiness.FAILED, allDone, null, false))
        assertEquals(Readiness.Problem.CLOSED, v(Readiness.PASSED, Readiness.FAILED, allDone, "x", true))
    }

    @Test
    fun `apply local keeps the server copy once and marks the row queued`() {
        val server = buildJsonObject {
            put("id", "c1"); put("asset_id", "A"); put("status", "pending"); put("server_version", 3)
            put("items", Readiness.itemsJson(Readiness.DEFAULT_ITEMS, withLocal = false))
        }
        val first = Readiness.applyLocal(server, Readiness.FAILED, Readiness.DEFAULT_ITEMS, " dent ", at)
        assertEquals("failed", first.str("status"))
        assertEquals("dent", first.str("notes"))
        assertEquals(at.toString(), first.str("completed_at"))
        assertEquals(true, first.bool(Readiness.KEY_QUEUED))
        assertEquals(server, first[Readiness.KEY_BASE])
        // A second decision before sync keeps the original server copy.
        val second = Readiness.applyLocal(first, Readiness.PASSED, allDone, null, at)
        assertEquals(server, second[Readiness.KEY_BASE])
        assertEquals("passed", second.str("status"))
        // Placeholders have no server copy (discard deletes them).
        val ph = Readiness.placeholder("p1", "A", "O1", "h1", at)
        assertNull(Readiness.applyLocal(ph, Readiness.PASSED, allDone, null, at)[Readiness.KEY_BASE])
    }

    @Test
    fun `items round trip and server items carry uploaded photos only`() {
        val items = listOf(
            Readiness.Item("cleaned", "Cleaned", true, photoIds = listOf("ph1", "ph2"),
                extra = buildJsonObject { put("photos", JsonArray(listOf(buildJsonObject { put("file_id", "old"); put("storage_key", "k0") }))) }),
            Readiness.Item("custom", "Tyres", false),
        )
        val local = Readiness.itemsJson(items)
        assertEquals(items, Readiness.parseItems(local))
        val wire = Readiness.serverItems(items) { id -> if (id == "ph1") "k1" else null }
        val cleaned = wire[0].jsonObject
        assertNull(cleaned["_photo_ids"])
        val photos = (cleaned["photos"] as JsonArray).map { it.jsonObject.str("file_id") }
        assertEquals(listOf("old", "ph1"), photos)
        assertEquals(setOf("key", "label", "done"), wire[1].jsonObject.keys)
    }

    @Test
    fun `current prefers the local row then the newest check`() {
        val old = rec(buildJsonObject { put("id", "a"); put("asset_id", "A"); put("status", "passed"); put("created_at", "2026-04-01T00:00:00Z") })
        val newer = rec(buildJsonObject { put("id", "b"); put("asset_id", "A"); put("status", "pending"); put("created_at", "2026-05-01T00:00:00Z") })
        val checks = listOf(old, newer).mapNotNull(Readiness::of)
        assertEquals("b", Readiness.current(checks)!!.id)
        val ph = rec(Readiness.placeholder("p", "A", null, "h", Instant.parse("2026-03-01T00:00:00Z")), RentalRecordEntity.STATE_PENDING)
        val withLocal = (listOf(old, newer, ph)).mapNotNull(Readiness::of)
        val cur = Readiness.current(withLocal)!!
        assertEquals("p", cur.id)
        assertTrue(cur.placeholder)
        assertTrue(cur.open)
        assertEquals(Readiness.DEFAULT_ITEMS.map { it.key }, cur.items.map { it.key })
    }

    @Test
    fun `screen edits open checks and shows decided ones read only until a new check starts`() {
        assertTrue(readinessUi(null, false).editable)
        val open = Readiness.of(rec(buildJsonObject { put("id", "a"); put("asset_id", "A"); put("status", "failed") }))!!
        assertEquals("a", readinessUi(open, false).check?.id)
        val passed = Readiness.of(rec(buildJsonObject { put("id", "a"); put("asset_id", "A"); put("status", "passed") }))!!
        assertFalse(readinessUi(passed, false).editable)
        assertNull(readinessUi(passed, true).check)
        assertTrue(readinessUi(passed, true).editable)
        val queued = Readiness.of(rec(buildJsonObject { put("id", "a"); put("asset_id", "A"); put("status", "passed") }, RentalRecordEntity.STATE_PENDING))!!
        assertFalse(readinessUi(queued, true).editable)
    }

    @Test
    fun `replay of an applied decision counts as success`() {
        assertTrue(Readiness.alreadyApplied(buildJsonObject { put("status", "passed") }, Readiness.PASSED))
        assertFalse(Readiness.alreadyApplied(buildJsonObject { put("status", "failed") }, Readiness.PASSED))
        assertFalse(Readiness.alreadyApplied(null, Readiness.PASSED))
    }

    @Test
    fun `request bodies match the documented shape`() {
        val create = JsonObject(RentalRequests.readinessCreate("A", null, null))
        assertEquals("A", create.str("asset_id"))
        val patch = JsonObject(RentalRequests.readinessUpdate("waived", Readiness.serverItems(allDone) { null }, "ok", 4))
        assertEquals("waived", patch.str("status"))
        assertEquals("ok", patch.str("notes"))
        assertEquals(4L, patch.long("server_version"))
        assertEquals(4, (patch["items"] as JsonArray).size)
        assertEquals(JsonPrimitive(true), (patch["items"] as JsonArray)[0].jsonObject["done"])
    }
}
