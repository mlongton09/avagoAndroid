package com.avago.feature.rentalsops.sync

import com.avago.feature.rentalsops.data.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrainOutcomeTest {

    @Test
    fun `ok is done and transport is retried`() {
        assertEquals(DrainOutcome.Done, DrainOutcome.classify(ApiResult.Ok(JsonObject(emptyMap()))))
        assertTrue(DrainOutcome.classify(ApiResult.Transport("timeout")) is DrainOutcome.Retry)
    }

    @Test
    fun `auth refresh, throttling and server errors are retried`() {
        listOf(401, 408, 425, 429, 500, 501, 503).forEach { s ->
            assertTrue(DrainOutcome.classify(ApiResult.Http(s, null, "x")) is DrainOutcome.Retry, "status $s")
        }
    }

    @Test
    fun `stage C is deployed, so 404 and 405 are final`() {
        listOf(404, 405).forEach { s ->
            assertTrue(DrainOutcome.classify(ApiResult.Http(s, "not_found", "x")) is DrainOutcome.Rejected, "status $s")
        }
    }

    @Test
    fun `403 not_assigned means reassigned and is never retried`() {
        val r = DrainOutcome.classify(ApiResult.Http(403, "not_assigned", "task is assigned to another driver"))
        assertEquals(DrainOutcome.Reassigned("task is assigned to another driver"), r)
        assertEquals(DrainOutcome.Reassigned("not_assigned"), DrainOutcome.classify(ApiResult.Http(403, "not_assigned", "")))
        // A plain permission 403 is a rejection, not a reassignment.
        assertTrue(DrainOutcome.classify(ApiResult.Http(403, "forbidden", "no")) is DrainOutcome.Rejected)
    }

    @Test
    fun `business errors are rejected with the server code`() {
        val r = DrainOutcome.classify(ApiResult.Http(400, "proof_required", "need proof"))
        assertEquals(DrainOutcome.Rejected("proof_required: need proof"), r)
        assertTrue(DrainOutcome.classify(ApiResult.Http(403, "forbidden", "no")) is DrainOutcome.Rejected)
        assertTrue(DrainOutcome.classify(ApiResult.Http(409, "allocation_conflict", "busy")) is DrainOutcome.Rejected)
    }

    private fun conflict(from: String?, code: String = "invalid_transition", status: Int = 409) =
        ApiResult.Http(status, code, "bad", from?.let { JsonObject(mapOf("from" to JsonPrimitive(it))) })

    @Test
    fun `replayed transition whose step already happened counts as applied`() {
        // Arrive replayed after the server already moved the task to arrived/completed.
        assertTrue(RentalOpsOutbox.alreadyApplied(conflict("arrived"), setOf("arrived", "completed")))
        assertTrue(RentalOpsOutbox.alreadyApplied(conflict("completed"), setOf("arrived", "completed")))
    }

    @Test
    fun `genuine invalid transitions and other conflicts are not swallowed`() {
        val reached = setOf("arrived", "completed")
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict("cancelled"), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict(null), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict("arrived", code = "allocation_conflict"), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(conflict("arrived", status = 400), reached))
        assertFalse(RentalOpsOutbox.alreadyApplied(ApiResult.Transport("x"), reached))
    }
}
