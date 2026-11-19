package com.avago.core.data.v2

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class V2LegacyProjectorTest {
    private val projector = V2LegacyProjector()

    @Test
    fun `every spec runs against the v2 schema`() = runBlocking {
        val db = V2Fixtures.business()
        for (spec in V2LegacyProjector.SPECS) {
            assertEquals(emptyList<Any>(), db.read { projector.project(it, spec.entityType) }, spec.entityType)
        }
    }

    @Test
    fun `assets and work orders map to the legacy vocabulary`() = runBlocking {
        val db = V2Fixtures.business()
        val loc = V2Uuid.v7()
        val asset = V2Uuid.v7()
        val wo = V2Uuid.v7()
        db.write { tx ->
            tx.execute(
                "INSERT INTO locations (id, created_at, updated_at, name, address_line1, locality) VALUES (?, 1, 2, 'Yard', 'Main 1', 'Oslo')",
                listOf(loc),
            )
            tx.execute(
                """INSERT INTO assets (id, created_at, updated_at, name, make_override, model_override, location_id,
                   status_code, status_since, global_uid, attributes) VALUES (?, 0, 1700000000000, 'Lift', 'Acme', 'L1', ?, 'offline', 0, ?, '{"a":1}')""",
                listOf(asset, loc, V2Uuid.v7()),
            )
            tx.execute(
                """INSERT INTO work_orders (id, created_at, updated_at, title, status, priority, due_at, attributes)
                   VALUES (?, 0, 0, 'Fix', 'open', 'high', 1700000000000, '{}')""",
                listOf(wo),
            )
            tx.execute(
                "INSERT INTO work_order_assets (created_at, work_order_id, asset_id, is_primary, sort_order) VALUES (0, ?, ?, 1, 0)",
                listOf(wo, asset),
            )
        }

        val a = db.read { projector.projectOne(it, "asset", asset) }!!
        assertEquals("Acme", a["make"]!!.jsonPrimitive.content)
        assertEquals("L1", a["model"]!!.jsonPrimitive.content)
        assertEquals("OFFLINE", a["current_status"]!!.jsonPrimitive.content)
        assertEquals("Oslo", a["city"]!!.jsonPrimitive.content)
        assertEquals("2023-11-14T22:13:20Z", a["updated_at"]!!.jsonPrimitive.content)

        val w = db.read { projector.projectOne(it, "work_order", wo) }!!
        assertEquals("reviewed", w["status"]!!.jsonPrimitive.content)
        assertEquals(asset, w["asset_id"]!!.jsonPrimitive.content)
        assertEquals("2023-11-14T22:13:20Z", w["due_date"]!!.jsonPrimitive.content)

        val link = db.read { projector.project(it, "work_order_asset") }.single()
        assertEquals(JsonPrimitive("$wo:$asset"), link["wo_asset_id"])

        assertNull(db.read { projector.projectOne(it, "asset", V2Uuid.v7()) })
    }
}
