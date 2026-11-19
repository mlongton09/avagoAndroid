package com.avago.core.data.v2

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class V2LegacyWritePlannerTest {
    private val planner = V2LegacyWritePlanner()
    private val acct = "acct"

    private fun calls(plan: V2BridgePlan) = (plan as V2BridgePlan.Send).calls

    @Test
    fun `new work order is created with the client id and parsed json fields`() {
        val payload = buildJsonObject {
            put("wo_id", "w1")
            put("title", "Fix lift")
            put("required_skills", "hydraulics, welding")
            put("due_date", "2026-03-01")
            put("status", "pending_review")
        }
        val call = calls(planner.plan(acct, "work_order", "w1", "insert", payload, current = null)).single()
        assertEquals("POST", call.method)
        assertEquals("/accounts/acct/work-orders", call.path)
        val body = call.body!!
        assertEquals(JsonPrimitive("w1"), body["wo_id"])
        assertEquals(JsonArray(listOf(JsonPrimitive("hydraulics"), JsonPrimitive("welding"))), body["required_skills"])
        assertEquals(JsonPrimitive("2026-03-01T12:00:00Z"), body["due_date"])
        assertTrue("status" !in body)
    }

    @Test
    fun `work order update sends only the diff and a transition for a status change`() {
        val current = buildJsonObject {
            put("wo_id", "w1")
            put("title", "Fix lift")
            put("priority", "high")
            put("estimated_effort_minutes", 30)
            put("status", "reviewed")
        }
        val payload = buildJsonObject {
            put("title", "Fix lift")
            put("priority", "low")
            put("estimated_effort_minutes", "30.0")
            put("status", "in_progress")
        }
        val calls = calls(planner.plan(acct, "work_order", "w1", "update", payload, current))
        assertEquals(listOf("PATCH", "POST"), calls.map { it.method })
        assertEquals(JsonObject(mapOf("priority" to JsonPrimitive("low"))), calls[0].body)
        assertEquals("/accounts/acct/work-orders/w1/transition", calls[1].path)
        assertEquals(JsonPrimitive("in_progress"), calls[1].body!!["status"])
    }

    @Test
    fun `unchanged update and delete of an unknown row are no-ops`() {
        val row = buildJsonObject { put("name", "Lift"); put("year", 2020) }
        assertEquals(emptyList<V2RestCall>(), calls(planner.plan(acct, "asset", "a1", "update", row, row)))
        assertEquals(emptyList<V2RestCall>(), calls(planner.plan(acct, "asset", "a1", "delete", null, current = null)))
    }

    @Test
    fun `soft-deleted payload deletes the server row`() {
        val payload = buildJsonObject { put("name", "Lift"); put("deleted_at", "2026-01-01T00:00:00Z") }
        val call = calls(planner.plan(acct, "asset", "a1", "update", payload, buildJsonObject { put("name", "Lift") })).single()
        assertEquals("DELETE", call.method)
        assertEquals("/accounts/acct/assets/a1", call.path)
    }

    @Test
    fun `checklist completion maps to a PUT only when newly completed`() {
        val current = buildJsonObject { put("wo_id", "w1"); put("completed", 0) }
        val done = buildJsonObject { put("wo_id", "w1"); put("is_completed", true); put("notes", "ok") }
        val call = calls(planner.plan(acct, "wo_checklist_item", "c1", "update", done, current)).single()
        assertEquals("PUT", call.method)
        assertEquals("/accounts/acct/work-orders/w1/checklist/c1", call.path)
        assertEquals(JsonPrimitive("ok"), call.body!!["notes"])

        val already = buildJsonObject { put("wo_id", "w1"); put("completed", 1) }
        assertEquals(emptyList<V2RestCall>(), calls(planner.plan(acct, "wo_checklist_item", "c1", "update", done, already)))
    }

    @Test
    fun `log create normalises date-only log_date and numeric meter`() {
        val payload = buildJsonObject {
            put("asset_id", "a1")
            put("log_date", "2026-02-03")
            put("meter", 1234)
            put("data", """{"k":1}""")
        }
        val body = calls(planner.plan(acct, "log", "l1", "insert", payload, null)).single().body!!
        assertEquals(JsonPrimitive("2026-02-03T12:00:00Z"), body["log_date"])
        assertEquals(JsonPrimitive("1234"), body["meter"])
        assertEquals(buildJsonObject { put("k", 1) }, body["data"])
        assertEquals(JsonPrimitive("l1"), body["log_id"])
    }

    @Test
    fun `unsupported entity types stay queued`() {
        val plan = planner.plan(acct, "doc", "d1", "insert", buildJsonObject { put("doc_id", "d1") }, null)
        assertTrue(plan is V2BridgePlan.Unsupported)
    }

    @Test
    fun `doc create links the asset and dates round-trip through the diff`() {
        val payload = buildJsonObject {
            put("doc_id", "d1"); put("asset_id", "a1"); put("title", "Receipt"); put("purchase_date", "2026-02-01")
        }
        val create = calls(planner.plan(acct, "doc", "d1", "insert", payload, current = null)).single()
        assertEquals("/accounts/acct/docs", create.path)
        assertEquals(JsonPrimitive("a1"), create.body!!["entity_id"])
        assertEquals(JsonPrimitive("asset"), create.body!!["entity_type"])
        assertEquals(JsonPrimitive("d1"), create.body!!["doc_id"])

        val current = buildJsonObject { put("title", "Receipt"); put("purchase_date", "2026-02-01T12:00:00Z") }
        assertTrue(calls(planner.plan(acct, "doc", "d1", "update", payload, current)).isEmpty())
        val unlinked = buildJsonObject { put("doc_id", "d2"); put("title", "Loose") }
        assertTrue(planner.plan(acct, "doc", "d2", "insert", unlinked, current = null) is V2BridgePlan.Unsupported)
    }
}