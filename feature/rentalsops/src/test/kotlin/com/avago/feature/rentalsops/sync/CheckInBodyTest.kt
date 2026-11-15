package com.avago.feature.rentalsops.sync

import com.avago.feature.rentalsops.data.ApiResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckInBodyTest {

    @Test
    fun `check-in body maps the action to the G5 status and keeps client uuid`() {
        val p = TaskEventPayload("t1", "start", "2026-06-10T08:00:00Z", lat = 1.0, lng = 2.0, accuracyM = 12.5f)
        val b = RentalOpsOutbox.checkInBody(p, "11111111-1111-1111-1111-111111111111", emptyList(), null)
        assertEquals("11111111-1111-1111-1111-111111111111", b["client_uuid"]!!.jsonPrimitive.content)
        assertEquals("en_route", b["status"]!!.jsonPrimitive.content)
        assertEquals("2026-06-10T08:00:00Z", b["at"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(1.0), b["lat"])
        assertEquals(JsonPrimitive(12.5f), b["accuracy_m"])
        assertNull(b["photo_file_ids"])

        val done = RentalOpsOutbox.checkInBody(
            p.copy(action = "complete", signedName = "Jo"), "u", listOf("f1"), "sig",
        )
        assertEquals("completed", done["status"]!!.jsonPrimitive.content)
        assertEquals("sig", done["signature_file_id"]!!.jsonPrimitive.content)
        assertEquals("Jo", done["signed_name"]!!.jsonPrimitive.content)
        assertEquals(1, (done["photo_file_ids"] as kotlinx.serialization.json.JsonArray).size)
        assertEquals("failed", RentalOpsOutbox.checkInBody(p.copy(action = "fail"), "u", emptyList(), null)["status"]!!.jsonPrimitive.content)
        assertEquals("arrived", RentalOpsOutbox.checkInBody(p.copy(action = "arrive"), "u", emptyList(), null)["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `half a coordinate pair and accuracy without a fix are never sent`() {
        val p = TaskEventPayload("t1", "arrive", "2026-06-10T08:00:00Z", lat = 1.0, lng = null, accuracyM = 5f)
        val b = RentalOpsOutbox.checkInBody(p, "u", emptyList(), null)
        assertNull(b["lat"])
        assertNull(b["lng"])
        assertNull(b["accuracy_m"])
        val a = RentalOpsOutbox.actionBody(p, emptyList(), null)
        assertNull(a["lat"])
        assertNull(a["client_uuid"])
        assertNull(a["status"])
    }

    @Test
    fun `only an older server without the route falls back to the stage C action`() {
        assertTrue(RentalOpsOutbox.checkInUnsupported(ApiResult.Http(404, null, "")))
        assertTrue(RentalOpsOutbox.checkInUnsupported(ApiResult.Http(405, null, "")))
        assertFalse(RentalOpsOutbox.checkInUnsupported(ApiResult.Http(404, "not_found", "task")))
        assertFalse(RentalOpsOutbox.checkInUnsupported(ApiResult.Http(409, "client_uuid_reused", "")))
        assertFalse(RentalOpsOutbox.checkInUnsupported(ApiResult.Transport("x", offline = true)))
    }
}
